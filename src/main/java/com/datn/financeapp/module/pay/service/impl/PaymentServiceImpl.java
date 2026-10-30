package com.datn.financeapp.module.pay.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.response.PageMeta;
import com.datn.financeapp.module.pay.config.PayOsProperties;
import com.datn.financeapp.module.pay.config.PaymentTopupProperties;
import com.datn.financeapp.module.pay.dto.req.PaymentCreateReq;
import com.datn.financeapp.module.pay.dto.res.PaymentCreateRes;
import com.datn.financeapp.module.pay.dto.res.PaymentListRes;
import com.datn.financeapp.module.pay.dto.res.PaymentStatusRes;
import com.datn.financeapp.module.pay.dto.res.WebhookOutcome;
import com.datn.financeapp.module.pay.entity.Payment;
import com.datn.financeapp.module.pay.enums.PaymentStatus;
import com.datn.financeapp.module.pay.enums.PaymentType;
import com.datn.financeapp.module.pay.enums.WebhookResult;
import com.datn.financeapp.module.pay.events.publisher.PaymentForfeitedEvent;
import com.datn.financeapp.module.pay.events.publisher.PaymentPaidEvent;
import com.datn.financeapp.module.pay.events.publisher.PaymentPartiallyPaidEvent;
import com.datn.financeapp.module.pay.events.publisher.PaymentTopupReminderEvent;
import com.datn.financeapp.module.pay.exceptions.PaymentException;
import com.datn.financeapp.module.pay.exceptions.PaymentNotFoundException;
import com.datn.financeapp.module.pay.helper.PayOsClientHelper;
import com.datn.financeapp.module.pay.helper.PaymentSpecs;
import com.datn.financeapp.module.pay.mapper.PaymentMapper;
import com.datn.financeapp.module.pay.repository.PaymentRepository;
import com.datn.financeapp.module.pay.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Triển khai xử lý nghiệp vụ thanh toán với cổng payOS.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentServiceImpl implements PaymentService {

    // mã trạng thái thành công chuẩn từ payOS
    private static final String SUCCESS_CODE = "00";

    // giới hạn độ dài nội dung chuyển khoản theo yêu cầu ngân hàng
    private static final int MAX_DESCRIPTION_LENGTH = 9;

    // modulo để rút ngắn mã đơn khi vượt quá độ dài tối đa
    private static final long DESCRIPTION_MOD = 10_000_000L;

    // mã đơn khởi đầu trong sequence để nhận diện đơn của hệ thống hay webhook thử
    private static final long FIRST_ORDER_CODE = 100_000L;

    private final PaymentRepository paymentRepository;
    private final PayOsClientHelper payOsClientHelper;
    private final PayOsProperties properties;
    private final PaymentTopupProperties topupProperties;
    private final PaymentMapper paymentMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Override
    public PaymentCreateRes createPayment(PaymentCreateReq req) {
        // tìm đơn đang mở của user bằng khoá bi quan không chờ để tránh deadlock
        Payment active;
        try {
            active = paymentRepository
                    .findActiveForUpdateNoWait(req.userId(), req.type(), List.of(PaymentStatus.PENDING, PaymentStatus.PARTIALLY_PAID))
                    .orElse(null);
        } catch (PessimisticLockingFailureException ex) {
            // bắt lỗi lock timeout chuyển thành 409 để FE thử lại
            throw new BusinessException(ErrorCode.REQUEST_IN_PROGRESS);
        }

        // nếu người dùng chưa có đơn nào đang mở thì tiến hành tạo mới
        if (active == null)
            return createNewPayment(req);

        // nếu người dùng đang có đơn thiếu tiền chưa thanh toán đủ
        if (active.getStatus() == PaymentStatus.PARTIALLY_PAID) {
            // kiểm tra xem đơn thiếu tiền đã quá hạn bù chưa
            if (isTopupOverdue(active)) {
                // đơn đã quá hạn bù thì đóng đơn và phạt giữ tiền
                forfeitOrder(active);
                paymentRepository.flush();
                // sau khi đóng đơn phạt thì cho phép tạo đơn mới
                return createNewPayment(req);
            }
            // nếu vẫn trong thời hạn bù tiền thì chặn không cho tạo đơn mới
            throw new BusinessException(ErrorCode.PAYMENT_PARTIALLY_PAID_PENDING);
        }

        // đóng đơn PENDING cũ bằng cách huỷ link payOS trước khi sinh đơn mới
        closeUnpaid(active, isPendingAndOverdue(active) ? PaymentStatus.EXPIRED : PaymentStatus.CANCELLED);

        // nếu đơn cũ đã được thanh toán đủ thì trả về kết quả thành công, không tạo đơn mới
        if (active.getStatus() == PaymentStatus.PAID)
            return paymentMapper.toCreateRes(active, null);

        // nếu đơn cũ thanh toán thiếu tiền thì chặn tạo mới, yêu cầu nạp bù
        if (active.getStatus() == PaymentStatus.PARTIALLY_PAID)
            throw new BusinessException(ErrorCode.PAYMENT_PARTIALLY_PAID_PENDING);

        // đẩy thay đổi trạng thái huỷ của đơn cũ xuống database
        paymentRepository.flush();

        // tạo đơn thanh toán mới với mã link mới
        return createNewPayment(req);
    }

    @Override
    public PaymentStatusRes getStatus(UUID userId, long orderCode) {
        // xác thực quyền sở hữu của người dùng mà không nạp thực thể
        if (!paymentRepository.existsByOrderCodeAndUserId(orderCode, userId)) {
            throw new PaymentNotFoundException();
        }

        // khoá dòng đơn hàng để đọc trạng thái chính xác nhất
        Payment payment = paymentRepository.findByOrderCodeForUpdate(orderCode)
                .orElseThrow(PaymentNotFoundException::new);

        // nếu đơn đang mở thì kiểm tra và đồng bộ trạng thái mới nhất từ cổng thanh toán
        if (payment.getStatus() == PaymentStatus.PENDING || payment.getStatus() == PaymentStatus.PARTIALLY_PAID) {
            syncOrderStatus(payment);
        }

        return paymentMapper.toStatusRes(payment);
    }

    @Override
    public PaymentStatusRes cancel(UUID userId, long orderCode) {
        // xác thực người dùng sở hữu đơn
        if (!paymentRepository.existsByOrderCodeAndUserId(orderCode, userId)) {
            throw new PaymentNotFoundException();
        }

        // khoá dòng để cập nhật huỷ đơn
        Payment payment = paymentRepository.findByOrderCodeForUpdate(orderCode)
                .orElseThrow(PaymentNotFoundException::new);

        // từ chối huỷ nếu đơn đã nhận một phần tiền và đang chờ bù
        if (payment.getStatus() == PaymentStatus.PARTIALLY_PAID) {
            throw new BusinessException(ErrorCode.PAYMENT_CANNOT_CANCEL_PARTIAL);
        }

        // chỉ xử lý huỷ khi đơn đang ở trạng thái PENDING
        if (payment.getStatus() == PaymentStatus.PENDING) {
            closeUnpaid(payment, PaymentStatus.CANCELLED);
        }

        return paymentMapper.toStatusRes(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentListRes listMine(UUID userId, PaymentStatus status, PaymentType type, Pageable pageable) {
        // truy vấn danh sách phân trang theo bộ lọc người dùng
        Page<Payment> page = paymentRepository.findAll(
                PaymentSpecs.filter(userId, status, type, null, null),
                newestFirst(pageable));
        return new PaymentListRes(page.map(paymentMapper::toStatusRes).getContent(), PageMeta.from(page));
    }

    @Override
    public WebhookOutcome processWebhook(Webhook webhook) {
        // xác thực tính hợp lệ của chữ ký webhook payOS
        WebhookData data = payOsClientHelper.verifyWebhook(webhook);
        if (data == null) {
            log.info("payOS webhook ignored: empty data");
            return WebhookOutcome.of(WebhookResult.IGNORED_EMPTY, webhook, null, "Webhook không có dữ liệu đơn");
        }

        // kiểm tra mã phản hồi giao dịch thành công từ ngân hàng
        if (!isSuccessfulWebhook(webhook, data)) {
            log.info("payOS webhook ignored: orderCode={}, code={}, dataCode={}",
                    data.getOrderCode(), webhook.getCode(), data.getCode());
            return WebhookOutcome.of(WebhookResult.IGNORED_NOT_SUCCESS, webhook, data,
                    "payOS báo không thành công (code=" + data.getCode() + ")");
        }

        // khoá dòng đơn hàng tương ứng với orderCode nhận được từ webhook
        Payment payment = paymentRepository.findByOrderCodeForUpdate(data.getOrderCode()).orElse(null);
        if (payment == null) {
            // cảnh báo nếu mã đơn hợp lệ nhưng không tìm thấy trong database (nghi vấn link mồ côi)
            if (data.getOrderCode() >= FIRST_ORDER_CODE) {
                log.error("[PAYMENT-RECONCILE] không có đơn cho orderCode={}, amount={}, paymentLinkId={} (link mồ côi?)",
                        data.getOrderCode(), data.getAmount(), data.getPaymentLinkId());
                return WebhookOutcome.of(WebhookResult.ORDER_NOT_FOUND, webhook, data,
                        "Không có đơn cho order_code này (link mồ côi?)");
            }
            log.info("payOS webhook thử/không phải của hệ thống: orderCode={}", data.getOrderCode());
            return WebhookOutcome.of(WebhookResult.IGNORED_EMPTY, webhook, data,
                    "Không phải đơn của hệ thống (webhook thử)");
        }

        // đối chiếu định danh link thanh toán để tránh nhầm lẫn giữa các đơn
        if (!sameLink(payment, data.getPaymentLinkId())) {
            log.error("[PAYMENT-RECONCILE] link không khớp: orderCode={}, savedLinkId={}, webhookLinkId={}",
                    payment.getOrderCode(), payment.getPayosLinkId(), data.getPaymentLinkId());
            return WebhookOutcome.of(WebhookResult.LINK_MISMATCH, webhook, data,
                    "payment_link_id không khớp link đã lưu của đơn");
        }

        // lấy số tiền thực thu nhận được từ webhook
        long paid = data.getAmount();

        // trường hợp thanh toán đủ hoặc thừa tiền
        if (paid >= payment.getAmount()) {
            WebhookResult result = markPaid(payment, paid);
            return WebhookOutcome.of(result, webhook, data, describe(result, payment, paid));
        }

        // trường hợp chuyển thiếu tiền
        if (paid > 0) {
            markPartiallyPaid(payment, paid);
            return WebhookOutcome.of(WebhookResult.UNDERPAID, webhook, data,
                    "Đã nhận một phần tiền (" + paid + "/" + payment.getAmount() + "), chuyển sang PARTIALLY_PAID");
        }

        return WebhookOutcome.of(WebhookResult.IGNORED_EMPTY, webhook, data, "Không có số tiền hợp lệ");
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findOverdueOrderCodes(Instant now, int limit) {
        // tìm danh sách các mã đơn PENDING đã quá hạn ban đầu
        return paymentRepository.findOverdueOrderCodes(PaymentStatus.PENDING, now, PageRequest.of(0, limit));
    }

    @Override
    public void expireIfOverdue(long orderCode) {
        // khoá dòng và kiểm tra điều kiện quá hạn
        Payment payment = paymentRepository.findByOrderCodeForUpdate(orderCode).orElse(null);
        if (!isPendingAndOverdue(payment)) {
            return;
        }
        // đóng đơn hết hạn ban đầu
        closeUnpaid(payment, PaymentStatus.EXPIRED);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findActivePendingOrderCodes(Instant now, int limit) {
        // lấy danh sách các đơn PENDING còn hạn để job đồng bộ tiền vào
        return paymentRepository.findActivePendingOrderCodes(now, PageRequest.of(0, limit));
    }

    @Override
    public void syncPendingOrder(long orderCode) {
        // khoá dòng và đồng bộ trạng thái đơn
        Payment payment = paymentRepository.findByOrderCodeForUpdate(orderCode).orElse(null);
        if (payment == null || payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }
        syncOrderStatus(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findOverdueTopupOrderCodes(Instant now, int limit) {
        // lấy danh sách các đơn thiếu tiền đã vượt quá hạn bù
        return paymentRepository.findOverdueTopupOrderCodes(now, PageRequest.of(0, limit));
    }

    @Override
    public void forfeitIfTopupOverdue(long orderCode) {
        // khoá dòng đơn thiếu tiền
        Payment payment = paymentRepository.findByOrderCodeForUpdate(orderCode).orElse(null);
        if (payment == null || payment.getStatus() != PaymentStatus.PARTIALLY_PAID) {
            return;
        }
        // nếu đã quá hạn bù thì tiến hành đóng đơn và giữ tiền
        if (isTopupOverdue(payment)) {
            forfeitOrder(payment);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findTopupReminderCandidates(Instant now, Instant reminderThreshold, int limit) {
        // lấy danh sách các đơn thiếu tiền sắp tới hạn chót cần nhắc nhở
        return paymentRepository.findTopupReminderCandidates(now, reminderThreshold, PageRequest.of(0, limit));
    }

    @Override
    public void remindTopupIfCandidate(long orderCode, Instant now, Instant reminderThreshold) {
        // khoá dòng kiểm tra điều kiện gửi thông báo nhắc
        Payment payment = paymentRepository.findByOrderCodeForUpdate(orderCode).orElse(null);
        if (payment == null || payment.getStatus() != PaymentStatus.PARTIALLY_PAID) {
            return;
        }
        // chỉ gửi thông báo khi chưa từng gửi nhắc nhở và nằm trong khung giờ nhắc
        if (payment.getTopupRemindedAt() == null
                && payment.getTopupDeadline() != null
                && !payment.getTopupDeadline().isBefore(now)
                && !payment.getTopupDeadline().isAfter(reminderThreshold)) {
            payment.setTopupRemindedAt(now);
            paymentRepository.save(payment);

            // tính số tiền còn thiếu để thông báo cho khách hàng
            long paid = payment.getAmountPaid() == null ? 0L : payment.getAmountPaid();
            long remaining = Math.max(0L, payment.getAmount() - paid);

            // phát sự kiện nhắc nhở để module notification gửi thông báo
            eventPublisher.publishEvent(new PaymentTopupReminderEvent(
                    payment.getUserId(),
                    payment.getType(),
                    payment.getReferenceId(),
                    payment.getOrderCode(),
                    payment.getAmount(),
                    paid,
                    remaining,
                    payment.getTopupDeadline()));
            log.info("payOS topup reminder sent: orderCode={}, remaining={}", payment.getOrderCode(), remaining);
        }
    }

    // -------------------------------- PHƯƠNG THỨC NỘI BỘ (PRIVATE) --------------------------------

    // tính tổng số tiền thực tế đã nhận qua link thanh toán
    private static long receivedOf(PaymentLink link) {
        if (link == null) return 0L;
        // tính tổng các giao dịch thành công đã nhận qua link
        long sum = link.getTransactions().stream()
                .mapToLong(vn.payos.model.v2.paymentRequests.Transaction::getAmount).sum();
        return Math.max(sum, link.getAmountPaid());
    }

    // xử lý cập nhật trạng thái đơn khi đã có tiền nạp vào link thanh toán
    private boolean processReceivedFunds(Payment payment, long received) {
        // nếu thực tế đã nhận đủ tiền thì ghi nhận thanh toán thành công
        if (received >= payment.getAmount()) {
            markPaid(payment, received);
            return true;
        }

        // nếu đã nhận một phần tiền thì chuyển sang trạng thái chờ nạp bù
        if (received > 0) {
            markPartiallyPaid(payment, received);
            return true;
        }

        return false;
    }

    // xử lý đóng đơn khi chưa thanh toán: kiểm tra tiền đã nhận trước khi huỷ link
    private void closeUnpaid(Payment payment, PaymentStatus targetStatus) {
        PaymentLink link = payOsClientHelper.getLink(payment.getOrderCode());
        long received = receivedOf(link);

        // nếu có tiền vào thì xử lý cập nhật trạng thái và không huỷ đơn
        if (processReceivedFunds(payment, received)) {
            return;
        }

        // nếu link chưa đóng thì gọi huỷ link trên payOS để khoá không cho chuyển tiền
        if (!isDead(link.getStatus())) {
            PaymentLink cancelled = payOsClientHelper.cancelLink(payment.getOrderCode());
            long afterCancel = receivedOf(cancelled);

            // kiểm tra tiền đến ngay lúc vừa huỷ link
            if (processReceivedFunds(payment, afterCancel)) {
                return;
            }
            if (!isDead(cancelled.getStatus())) {
                log.error("[PAYMENT-RECONCILE] huỷ link không hiệu lực: orderCode={}, status={}",
                        payment.getOrderCode(), cancelled.getStatus());
                throw new PaymentException(ErrorCode.PAYMENT_PROVIDER_ERROR);
            }
        }
        payment.setStatus(targetStatus);
    }

    // đồng bộ trạng thái đơn hàng từ thông tin link thanh toán của payOS
    private void syncOrderStatus(Payment payment) {
        try {
            // nếu đơn PENDING đã quá hạn ban đầu thì đóng đơn EXPIRED
            if (payment.getStatus() == PaymentStatus.PENDING && isPendingAndOverdue(payment)) {
                closeUnpaid(payment, PaymentStatus.EXPIRED);
                return;
            }

            // nếu đơn PARTIALLY_PAID đã quá hạn bù thì đóng đơn phạt FORFEITED
            if (payment.getStatus() == PaymentStatus.PARTIALLY_PAID && isTopupOverdue(payment)) {
                forfeitOrder(payment);
                return;
            }

            // đọc thông tin link trực tiếp từ payOS
            PaymentLink link = payOsClientHelper.getLink(payment.getOrderCode());
            long received = receivedOf(link);

            // nếu có tiền vào thì cập nhật trạng thái tương ứng
            if (processReceivedFunds(payment, received)) {
                return;
            }

            // nếu link đã bị huỷ hoặc hết hạn trên payOS
            if (isDead(link.getStatus())) {
                if (link.getStatus() == PaymentLinkStatus.CANCELLED) {
                    payment.setStatus(PaymentStatus.CANCELLED);
                } else {
                    payment.setStatus(PaymentStatus.EXPIRED);
                }
            }
        } catch (PaymentException ex) {
            log.warn("payOS sync failed: orderCode={}, code={}", payment.getOrderCode(), ex.getCode());
        }
    }

    // ghi nhận trạng thái thanh toán thiếu tiền và lên lịch hạn chót bù tiền
    private void markPartiallyPaid(Payment payment, long received) {
        payment.setAmountPaid(received);
        payment.setStatus(PaymentStatus.PARTIALLY_PAID);

        // chỉ khởi tạo hạn chót bù tiền ở lần đầu tiên phát hiện thiếu
        if (payment.getTopupDeadline() == null) {
            payment.setTopupDeadline(clock.instant().plusSeconds(topupProperties.seconds()));
        }
        paymentRepository.save(payment);

        long remaining = Math.max(0L, payment.getAmount() - received);

        // phát sự kiện thông báo thiếu tiền
        eventPublisher.publishEvent(new PaymentPartiallyPaidEvent(
                payment.getUserId(),
                payment.getType(),
                payment.getReferenceId(),
                payment.getOrderCode(),
                payment.getAmount(),
                received,
                remaining,
                payment.getTopupDeadline()));
        log.info("payOS payment partially paid: orderCode={}, received={}, remaining={}",
                payment.getOrderCode(), received, remaining);
    }

    // xử lý đóng đơn quá hạn bù tiền và tịch thu số tiền đã nhận
    private void forfeitOrder(Payment payment) {
        // huỷ link trên payOS để chặn không cho khách chuyển tiền tiếp
        try {
            payOsClientHelper.cancelLink(payment.getOrderCode());
        } catch (Exception ex) {
            log.warn("Huỷ link payOS khi phạt đơn không thành công (có thể link đã đóng): orderCode={}",
                    payment.getOrderCode());
        }

        // cập nhật trạng thái đóng đơn vĩnh viễn và thời điểm xử phạt
        payment.setStatus(PaymentStatus.FORFEITED);
        payment.setForfeitedAt(clock.instant());
        paymentRepository.save(payment);

        long paid = payment.getAmountPaid() == null ? 0L : payment.getAmountPaid();
        log.error("[PAYMENT-RECONCILE] Đơn quá hạn bù tiền, đóng vĩnh viễn và giữ tiền: orderCode={}, amountPaid={}",
                payment.getOrderCode(), paid);

        // phát sự kiện đơn bị phạt đóng vĩnh viễn
        eventPublisher.publishEvent(new PaymentForfeitedEvent(
                payment.getUserId(),
                payment.getType(),
                payment.getReferenceId(),
                payment.getOrderCode(),
                payment.getAmount(),
                paid,
                payment.getForfeitedAt()));
    }

    // kiểm tra xem link đã kết thúc trạng thái trên cổng thanh toán chưa
    private boolean isDead(PaymentLinkStatus status) {
        return status == PaymentLinkStatus.CANCELLED
                || status == PaymentLinkStatus.EXPIRED
                || status == PaymentLinkStatus.FAILED;
    }

    // tạo mới đơn thanh toán và khởi tạo link checkout với payOS
    private PaymentCreateRes createNewPayment(PaymentCreateReq req) {
        // sinh mã đơn số nguyên từ database sequence
        long orderCode = paymentRepository.nextOrderCode();

        // tính thời hạn hết hạn đơn theo cấu hình
        Instant expiresAt = clock.instant().plusSeconds(properties.expiredMinutes() * 60);

        // tạo chuỗi nội dung chuyển khoản ngắn gọn
        String description = buildDescription(orderCode);

        // gọi SDK payOS để sinh link checkout
        CreatePaymentLinkResponse link = payOsClientHelper.createLink(
                orderCode, req.amount(), description, expiresAt.getEpochSecond());

        // đăng ký huỷ link bù trừ nếu transaction bị rollback (phòng ngừa link mồ côi)
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        payOsClientHelper.cancelLink(orderCode);
                    } catch (Exception ex) {
                        log.error("[PAYMENT-RECONCILE] không huỷ được link mồ côi: orderCode={}", orderCode, ex);
                    }
                }
            }
        });

        // lưu thực thể đơn thanh toán vào cơ sở dữ liệu
        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .orderCode(orderCode)
                .userId(req.userId())
                .type(req.type())
                .referenceId(req.referenceId())
                .amount(req.amount())
                .status(PaymentStatus.PENDING)
                .payosLinkId(link.getPaymentLinkId())
                .expiresAt(expiresAt)
                .build();
        paymentRepository.saveAndFlush(payment);

        log.info("payOS payment link created: orderCode={}, type={}", orderCode, req.type());
        return paymentMapper.toCreateRes(payment, link.getCheckoutUrl());
    }

    // ghi nhận thanh toán thành công và kích hoạt đơn hàng
    private WebhookResult markPaid(Payment payment, Long paidAmount) {
        // nếu đơn đã thanh toán trước đó thì bỏ qua
        if (payment.getStatus() == PaymentStatus.PAID) {
            return WebhookResult.ALREADY_PAID;
        }

        // nếu đơn đã bị đóng trước đó mà tiền vẫn về thì ghi log cảnh báo đối soát
        if (payment.getStatus() != PaymentStatus.PENDING && payment.getStatus() != PaymentStatus.PARTIALLY_PAID) {
            log.error("[PAYMENT-RECONCILE] nhận tiền cho đơn đã {}: orderCode={}, paid={}",
                    payment.getStatus(), payment.getOrderCode(), paidAmount);
            return WebhookResult.ORDER_CLOSED;
        }

        // kiểm tra số tiền thực thu
        if (paidAmount == null || paidAmount < payment.getAmount()) {
            log.error("[PAYMENT-RECONCILE] thiếu tiền: expected={}, paid={}, orderCode={}",
                    payment.getAmount(), paidAmount, payment.getOrderCode());
            return WebhookResult.UNDERPAID;
        }

        // ghi nhận trường hợp người dùng chuyển dư tiền
        if (paidAmount > payment.getAmount()) {
            log.info("Payment {} trả dư {}", payment.getOrderCode(), paidAmount - payment.getAmount());
        }

        // cập nhật trạng thái đơn thành công
        payment.setAmountPaid(paidAmount);
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(clock.instant());
        paymentRepository.save(payment);

        // phát sự kiện thanh toán thành công để module thụ hưởng (user premium) kích hoạt dịch vụ
        eventPublisher.publishEvent(new PaymentPaidEvent(
                payment.getUserId(), payment.getType(), payment.getReferenceId(), payment.getOrderCode()));
        log.info("payOS payment success: orderCode={}", payment.getOrderCode());
        return WebhookResult.PROCESSED;
    }

    // mô tả kết quả xử lý webhook phục vụ bảng nhật ký quản trị
    private String describe(WebhookResult result, Payment payment, Long paidAmount) {
        return switch (result) {
            case PROCESSED -> paidAmount != null && paidAmount > payment.getAmount()
                    ? "Đã giao hàng, user trả dư " + (paidAmount - payment.getAmount())
                    : "Đã giao hàng";
            case ALREADY_PAID -> "Đơn đã PAID trước đó (payOS gửi lại)";
            case ORDER_CLOSED -> "Đơn đã " + payment.getStatus() + " mà vẫn nhận tiền, không giao hàng";
            case UNDERPAID -> "Thu " + paidAmount + " < số đơn " + payment.getAmount() + ", chuyển PARTIALLY_PAID";
            default -> null;
        };
    }

    // kiểm tra đơn PENDING đã quá hạn thanh toán ban đầu chưa
    private boolean isPendingAndOverdue(Payment payment) {
        return payment != null
                && payment.getStatus() == PaymentStatus.PENDING
                && !payment.getExpiresAt().isAfter(clock.instant());
    }

    // kiểm tra đơn PARTIALLY_PAID đã quá hạn chót nạp bù tiền chưa
    private boolean isTopupOverdue(Payment payment) {
        return payment != null
                && payment.getStatus() == PaymentStatus.PARTIALLY_PAID
                && payment.getTopupDeadline() != null
                && !payment.getTopupDeadline().isAfter(clock.instant());
    }

    // kiểm tra trùng khớp định danh link thanh toán
    private boolean sameLink(Payment payment, String webhookLinkId) {
        if (payment.getPayosLinkId() == null || webhookLinkId == null || webhookLinkId.isBlank()) {
            return true;
        }
        return payment.getPayosLinkId().equals(webhookLinkId);
    }

    // kiểm tra mã thành công từ gói tin webhook
    private boolean isSuccessfulWebhook(Webhook webhook, WebhookData data) {
        return Boolean.TRUE.equals(webhook.getSuccess())
                && SUCCESS_CODE.equals(webhook.getCode())
                && SUCCESS_CODE.equals(data.getCode());
    }

    // cấu hình sắp xếp đơn mới nhất lên đầu
    private Pageable newestFirst(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    // tạo mã mô tả đơn hàng theo quy cách DH + orderCode
    private String buildDescription(long orderCode) {
        String value = "DH" + orderCode;
        if (value.length() <= MAX_DESCRIPTION_LENGTH) {
            return value;
        }
        return "DH" + Math.floorMod(orderCode, DESCRIPTION_MOD);
    }
}

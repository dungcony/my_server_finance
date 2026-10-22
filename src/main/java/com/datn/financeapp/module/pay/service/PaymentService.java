package com.datn.financeapp.module.pay.service;

import com.datn.financeapp.module.pay.dto.req.PaymentCreateReq;
import com.datn.financeapp.module.pay.dto.res.PaymentCreateRes;
import com.datn.financeapp.module.pay.dto.res.PaymentListRes;
import com.datn.financeapp.module.pay.dto.res.PaymentStatusRes;
import com.datn.financeapp.module.pay.dto.res.WebhookOutcome;
import com.datn.financeapp.module.pay.enums.PaymentStatus;
import com.datn.financeapp.module.pay.enums.PaymentType;
import org.springframework.data.domain.Pageable;
import vn.payos.model.webhooks.Webhook;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Giao diện dịch vụ nghiệp vụ chính của phân hệ thanh toán payOS.
 * Tuân thủ quy ước khoá: khoá đơn thanh toán trước rồi mới tới các tài nguyên liên quan.
 */
public interface PaymentService {

    // tạo đơn thanh toán mới; nếu đang có đơn PENDING cũ thì huỷ link cũ và tạo đơn mới
    PaymentCreateRes createPayment(PaymentCreateReq req);

    // lấy thông tin trạng thái đơn của chính người dùng sở hữu
    PaymentStatusRes getStatus(UUID userId, long orderCode);

    // người dùng chủ động huỷ đơn thanh toán PENDING của chính mình
    PaymentStatusRes cancel(UUID userId, long orderCode);

    // xem danh sách lịch sử các giao dịch thanh toán của người dùng hiện tại
    PaymentListRes listMine(UUID userId, PaymentStatus status, PaymentType type, Pageable pageable);

    // xử lý thông báo biến động thanh toán từ webhook của payOS
    WebhookOutcome processWebhook(Webhook webhook);

    // tìm danh sách các mã đơn thanh toán PENDING đã quá hạn ban đầu
    List<Long> findOverdueOrderCodes(Instant now, int limit);

    // xử lý hết hạn cho đơn PENDING quá hạn: huỷ link payOS và chuyển EXPIRED hoặc PAID
    void expireIfOverdue(long orderCode);

    // tìm danh sách mã đơn PENDING còn hạn để job quét đồng bộ số tiền đã chuyển từ payOS
    List<Long> findActivePendingOrderCodes(Instant now, int limit);

    // đồng bộ trạng thái đơn PENDING từ payOS (phát hiện chuyển thiếu PARTIALLY_PAID hoặc chuyển đủ PAID)
    void syncPendingOrder(long orderCode);

    // tìm danh sách mã đơn PARTIALLY_PAID đã quá hạn bù tiền
    List<Long> findOverdueTopupOrderCodes(Instant now, int limit);

    // xử lý đơn quá hạn bù tiền: huỷ link payOS, chuyển trạng thái FORFEITED và giữ tiền
    void forfeitIfTopupOverdue(long orderCode);

    // tìm danh sách mã đơn PARTIALLY_PAID sắp đến hạn cần gửi thông báo nhắc
    List<Long> findTopupReminderCandidates(Instant now, Instant reminderThreshold, int limit);

    // phát thông báo nhắc nhở nạp bù tiền cho người dùng nếu đơn thoả mãn điều kiện
    void remindTopupIfCandidate(long orderCode, Instant now, Instant reminderThreshold);
}

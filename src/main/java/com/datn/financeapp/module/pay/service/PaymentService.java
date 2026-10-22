package com.datn.financeapp.module.pay.service;

import com.datn.financeapp.module.pay.dto.req.PaymentCreateReq;
import com.datn.financeapp.module.pay.dto.res.PaymentCreateRes;
import com.datn.financeapp.module.pay.dto.res.PaymentListRes;
import com.datn.financeapp.module.pay.dto.res.PaymentStatusRes;
import com.datn.financeapp.module.pay.enums.PaymentStatus;
import com.datn.financeapp.module.pay.enums.PaymentType;
import org.springframework.data.domain.Pageable;
import vn.payos.model.webhooks.Webhook;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * API công khai của module thanh toán. Module sở hữu hàng hoá tính giá rồi gọi {@link #createPayment};
 * giao hàng khi nghe {@code PaymentPaidEvent}.
 * <p>
 * <b>Quy ước khoá:</b> Mọi thao tác đổi trạng thái đều tuân theo thứ tự khoá: đơn thanh toán trước,
 * sau đó mới tới tài nguyên khác (như User).
 * </p>
 */
public interface PaymentService {

    /**
     * Tạo đơn thanh toán mới. Nếu user còn đơn PENDING thì huỷ link cũ và tạo đơn mới + link mới.
     * Nếu trước khi huỷ mà payOS báo đơn cũ đã thanh toán (PAID) thì hoàn tất đơn cũ và trả về, không tạo mới.
     */
    PaymentCreateRes createPayment(PaymentCreateReq req);

    /** Trạng thái đơn của chính user; đơn của người khác coi như không tồn tại. */
    PaymentStatusRes getStatus(UUID userId, long orderCode);

    /**
     * Huỷ đơn thanh toán PENDING của chính user.
     * Thao tác có tính idempotent: nếu đơn đã kết thúc (PAID/EXPIRED/CANCELLED), trả lại trạng thái hiện tại.
     */
    PaymentStatusRes cancel(UUID userId, long orderCode);

    PaymentListRes listMine(UUID userId, PaymentStatus status, PaymentType type, Pageable pageable);

    /** Xử lý webhook payOS: sai chữ ký thì ném lỗi, dữ liệu không khớp thì bỏ qua, đơn đã xử lý thì bỏ qua. */
    void processWebhook(Webhook webhook);

    List<Long> findOverdueOrderCodes(Instant now, int limit);

    /** Huỷ link payOS rồi ghi EXPIRED cho đơn PENDING đã quá hạn; payOS báo đã trả thì ghi PAID. */
    void expireIfOverdue(long orderCode);
}

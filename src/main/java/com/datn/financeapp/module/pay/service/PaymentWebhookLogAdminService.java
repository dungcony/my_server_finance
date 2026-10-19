package com.datn.financeapp.module.pay.service;

import com.datn.financeapp.module.pay.dto.res.WebhookLogListRes;
import com.datn.financeapp.module.pay.dto.res.WebhookSummaryRes;
import com.datn.financeapp.module.pay.enums.WebhookResult;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

/**
 * Xem nhật ký webhook payOS cho quản trị viên (chỉ đọc).
 */
public interface PaymentWebhookLogAdminService {

    WebhookLogListRes list(WebhookResult result, boolean onlyFailed, Long orderCode,
                           LocalDate fromDate, LocalDate toDate, Pageable pageable);

    /** Thống kê {@code hours} giờ gần nhất; {@code hours} ngoài 1..168 được đưa về mốc gần nhất. */
    WebhookSummaryRes summary(int hours);
}

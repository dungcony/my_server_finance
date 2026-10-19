package com.datn.financeapp.module.pay.dto.res;

import com.datn.financeapp.module.pay.enums.WebhookResult;

import java.time.Instant;
import java.util.UUID;

/**
 * Một dòng nhật ký webhook trên dashboard admin. {@code occurrences > 1} và {@code aggregated = true} nghĩa là
 * dòng gộp nhiều request giả mạo vượt giới hạn mỗi phút.
 */
public record WebhookLogRes(
        UUID id,
        Instant receivedAt,
        Long orderCode,
        String paymentLinkId,
        Long amount,
        String payosCode,
        Boolean success,
        WebhookResult result,
        String message,
        int httpStatus,
        int durationMs,
        String clientIp,
        int occurrences,
        boolean aggregated
) {
}

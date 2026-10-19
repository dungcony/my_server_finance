package com.datn.financeapp.module.pay.dto.res;

import com.datn.financeapp.module.pay.enums.WebhookResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Thống kê webhook trong {@code hours} giờ gần nhất.
 *
 * @param total              tổng số request (đã tính cả request gộp)
 * @param failed             số request thuộc nhóm lỗi
 * @param failureRate        failed / total, 0 khi chưa có request nào
 * @param countsByResult     đếm theo từng loại kết quả (đủ mọi loại, loại không có là 0)
 * @param lastSuccessAt      lần gần nhất webhook xác nhận đơn đã trả (PROCESSED/ALREADY_PAID), không bị giới hạn bởi {@code hours}
 * @param lastFailureAt      lần gần nhất webhook lỗi, không bị giới hạn bởi {@code hours}
 * @param paidWithoutWebhook order_code các đơn PAID quá {@code missingMinutes} phút mà không có webhook xác nhận (tối đa 50)
 * @param missingMinutes     ngưỡng chờ webhook, lấy từ cấu hình
 */
public record WebhookSummaryRes(
        int hours,
        long total,
        long failed,
        double failureRate,
        Map<WebhookResult, Long> countsByResult,
        Instant lastSuccessAt,
        Instant lastFailureAt,
        List<Long> paidWithoutWebhook,
        int missingMinutes
) {
}

package com.datn.financeapp.goal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Một mục tiêu trong {@code GET /goals} (api/09 mục B1).
 *
 * <p>{@code savedAmount} LUÔN lấy từ giá trị đọc dưới CSDL (do trigger ghi), không bao giờ tự
 * cộng ở Java.
 */
public record GoalListItemResponse(
        UUID id,
        String name,
        Long targetAmount,
        Long savedAmount,
        Long missingAmount,
        BigDecimal progress,
        String progressLabel,
        LocalDate targetDate,
        Integer daysRemaining,
        String status,
        IconSummary icon,
        WalletSummary wallet,
        Suggestion suggestion,
        int contributionCount) {

    public record IconSummary(String code, String pathData) {}

    public record WalletSummary(UUID id, String name) {}

    /** api/09 mục B1 "Cách tính gợi ý" — null khi mục tiêu không đặt {@code target_date}. */
    public record Suggestion(Long monthlyRequired, String content, String assessment) {}
}

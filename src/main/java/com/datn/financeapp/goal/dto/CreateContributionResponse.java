package com.datn.financeapp.goal.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Body phản hồi 201 của {@code POST /goals/{id}/contributions} (api/09 mục B3).
 *
 * <p>Khối {@code goal} lấy từ giá trị ĐỌC LẠI sau khi trigger chạy — không phải từ instance đã
 * load trước khi chèn {@code goal_contributions}, vốn còn mang {@code saved_amount}/{@code status}
 * cũ.
 *
 * <p>{@code transaction} bằng null khi nạp ở chế độ {@code create_transaction = false}.
 */
public record CreateContributionResponse(
        Contribution contribution, GoalProgress goal, TransactionSummary transaction, NewBalance newBalance) {

    public record Contribution(UUID id, Long amount, LocalDate contributedDate, UUID transactionId) {}

    public record GoalProgress(Long savedAmount, java.math.BigDecimal progress, String status) {}

    public record TransactionSummary(UUID id, String type, Long amount) {}

    public record NewBalance(UUID walletId, Long balance) {}
}

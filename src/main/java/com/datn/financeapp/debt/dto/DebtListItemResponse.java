package com.datn.financeapp.debt.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Một phần tử của {@code GET /debts} (api/08 mục 1).
 *
 * <p>{@code remainingAmount}, {@code paidRatio}, {@code daysRemaining}, {@code isOverdue} là số
 * DẪN XUẤT, tính bằng Java từ dữ liệu đã đọc — không có cột nào trong bảng, và cũng không cần
 * view riêng vì số bản ghi nợ của một người rất nhỏ.
 */
public record DebtListItemResponse(
        UUID id,
        String type,
        String counterpartyName,
        Long principalAmount,
        Long paidAmount,
        Long remainingAmount,
        BigDecimal paidRatio,
        LocalDate issuedDate,
        LocalDate dueDate,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer daysRemaining,
        boolean isOverdue,
        String status,
        String note,
        WalletSummary wallet,
        int paymentCount) {

    public record WalletSummary(UUID id, String name) {}
}

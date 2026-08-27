package com.datn.financeapp.debt.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Body {@code GET /debts/{id}} (api/08 mục 3) — như mục 1 kèm lịch sử trả và giao dịch gốc.
 *
 * <p>{@code @JsonUnwrapped} để các trường của {@link DebtListItemResponse} nằm PHẲNG ở cấp cao
 * nhất đúng như api/08 mô tả ("trả về như mục 1, kèm..."), không bọc thêm một lớp {@code debt}.
 */
public record DebtDetailResponse(
        @JsonUnwrapped DebtListItemResponse debt,
        List<PaymentHistoryItem> paymentHistory,
        OriginTransaction originTransaction) {

    public record PaymentHistoryItem(UUID id, Long amount, LocalDate paidDate, String note, UUID transactionId) {}

    public record OriginTransaction(UUID id, Long amount, LocalDate date) {}
}

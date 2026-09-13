package com.datn.financeapp.debt.dto.response;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Body phản hồi 201 của {@code POST /debts/{id}/payments} (api/08 mục 5).
 *
 * <p>Khối {@code debt} lấy từ bản ghi ĐỌC LẠI sau khi trigger chạy — không phải từ instance đã
 * load trước khi chèn {@code debt_payments}, vốn còn mang {@code paid_amount}/{@code status} cũ.
 */
public record CreatePaymentResponse(
        Payment payment, DebtProgress debt, TransactionSummary transaction, NewBalance newBalance) {

    public record Payment(UUID id, Long amount, LocalDate paidDate, String note) {}

    public record DebtProgress(Long paidAmount, Long remainingAmount, String status) {}

    public record TransactionSummary(UUID id, String type, Long amount) {}

    public record NewBalance(UUID walletId, Long balance) {}
}

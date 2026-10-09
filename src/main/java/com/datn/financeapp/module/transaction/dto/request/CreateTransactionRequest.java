package com.datn.financeapp.module.transaction.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Body của POST /transactions. Validate chéo-field (category bắt buộc/rỗng, destination bắt buộc/rỗng theo {@code type})
 * xử lý bằng {@code if} tường minh trong service.
 *
 * @param type                loại giao dịch (expense, income, transfer)
 * @param amount              số tiền giao dịch
 * @param walletId            ID ví phát sinh giao dịch
 * @param destinationWalletId ID ví đích (chỉ dùng cho transfer)
 * @param categoryId          ID danh mục (dùng cho expense/income)
 * @param date                ngày giao dịch
 * @param displayName         tên hiển thị
 * @param note                ghi chú
 * @param source              nguồn tạo giao dịch
 * @param countsInReport      có tính vào báo cáo hay không
 * @param receiptUrl          URL hóa đơn
 * @param draftId             ID bản nháp AI (nếu có)
 */
public record CreateTransactionRequest(
        @NotNull @Pattern(regexp = "^(expense|income|transfer)$") String type,
        @NotNull Long amount,
        @NotNull UUID walletId,
        UUID destinationWalletId,
        UUID categoryId,
        LocalDate date,
        @Size(max = 150) String displayName,
        @Size(max = 500) String note,
        String source,
        Boolean countsInReport,
        String receiptUrl,
        UUID draftId) {
}

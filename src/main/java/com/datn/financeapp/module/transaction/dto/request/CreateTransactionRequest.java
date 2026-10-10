package com.datn.financeapp.module.transaction.dto.request;

import com.datn.financeapp.module.transaction.enums.TransactionSource;
import com.datn.financeapp.module.transaction.enums.TransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 *
 * @param type        loại giao dịch (expense, income, transfer)
 * @param amount      số tiền giao dịch
 * @param walletId    ID ví phát sinh giao dịch
 * @param categoryId  ID danh mục (dùng cho expense/income)
 * @param date        ngày giao dịch
 * @param displayName tên hiển thị
 * @param note        ghi chú
 * @param source      Nguồn giao dịch(tự ghi hay nhờ AI, hoặc là do reccurring tự động tạo)
 * @param receiptUrl  URL hóa đơn
 * @param draftId     ID bản nháp AI (nếu có)
 */
public record CreateTransactionRequest(
        @NotNull TransactionType type,
        @NotNull Long amount,
        @NotNull UUID walletId,
        UUID categoryId,
        LocalDate date,
        @Size(max = 150) String displayName,
        @Size(max = 500) String note,
        TransactionSource source,
        String receiptUrl,
        UUID draftId) {
}

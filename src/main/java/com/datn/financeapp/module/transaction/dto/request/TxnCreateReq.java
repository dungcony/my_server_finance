package com.datn.financeapp.module.transaction.dto.request;

import com.datn.financeapp.module.transaction.enums.TxnSource;
import com.datn.financeapp.module.transaction.enums.TxnType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 *
 * @param type        loại giao dịch (EXPENSE, INCOME, LOAN)
 * @param amount      số tiền giao dịch
 * @param walletId    ID ví phát sinh giao dịch
 * @param categoryId  ID danh mục
 * @param occurredAt  thời gian giao dịch
 * @param displayName tên hiển thị
 * @param note        ghi chú
 */
public record TxnCreateReq(
        @NotNull(message = "Type là bắt buộc")
        TxnType type,

        @NotNull(message = "số tiền là bắt buộc")
        Long amount,

        @NotNull(message = "cần lựa chọn ví để giao dịch")
        UUID walletId,

        UUID categoryId,

        @NotNull(message = "Phải có thời gian giao dịch")
        @PastOrPresent(message = "Thời gian giao dịch không được ở tương lai.")
        Instant occurredAt,

        @Size(max = 150, message = "Tên hiển thị tối đa 150 ký tự.")
        String displayName,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự.")
        String note) {
}

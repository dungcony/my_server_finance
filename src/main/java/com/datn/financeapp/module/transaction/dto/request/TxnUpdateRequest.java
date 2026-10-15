package com.datn.financeapp.module.transaction.dto.request;

import com.datn.financeapp.module.transaction.enums.TxnType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;


public record TxnUpdateRequest(
        @NotNull(message = "Loại giao dịch không được để trống.")
        TxnType type,

        @NotNull(message = "Số tiền không được để trống.")
        @Positive(message = "Số tiền phải lớn hơn 0.")
        Long amount,

        @NotNull(message = "Ví không được để trống.")
        UUID walletId,

        UUID categoryId,
        LocalDate date,

        @Size(max = 150, message = "Tên hiển thị tối đa 150 ký tự.")
        String displayName,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự.")
        String note,

        String receiptUrl) {
}

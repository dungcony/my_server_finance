package com.datn.financeapp.module.transaction.dto.request;

import com.datn.financeapp.module.transaction.enums.TxnType;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;


public record TxnUpdateReq(
        TxnType type,

        @Positive(message = "Số tiền phải lớn hơn 0.")
        Long amount,

        UUID walletId,

        String categoryCode,

        @PastOrPresent(message = "Thời gian giao dịch không được ở tương lai.")
        Instant occuredAt,

        @Size(max = 150, message = "Tên hiển thị tối đa 150 ký tự.")
        String displayName,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự.")
        String note) {
}

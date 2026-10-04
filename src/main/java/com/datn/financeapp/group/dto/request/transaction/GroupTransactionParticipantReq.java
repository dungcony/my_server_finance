package com.datn.financeapp.group.dto.request.transaction;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record GroupTransactionParticipantReq(
        @NotNull(message = "ID thành viên tham gia không được để trống")
        UUID userId,

        @NotNull(message = "Số tiền chia của thành viên không được để trống")
        @Positive(message = "Số tiền chia của thành viên phải lớn hơn 0")
        Long shareAmount) {
}

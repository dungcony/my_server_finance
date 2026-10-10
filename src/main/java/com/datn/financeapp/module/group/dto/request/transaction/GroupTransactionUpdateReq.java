package com.datn.financeapp.module.group.dto.request.transaction;

import com.datn.financeapp.module.group.enums.MoneySource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

public record GroupTransactionUpdateReq(
        @Positive(message = "Số tiền phải lớn hơn 0.") @Max(value = 999999999999L, message = "Số tiền vượt quá giới hạn cho phép.") Long amount,
        Instant occurredAt,
        LocalDate date,
        MoneySource moneySource,
        UUID categoryId,
        UUID transactorId,
        @Size(max = 255, message = "Ghi chú không được vượt quá 255 ký tự.") String note,
        @Valid List<GroupTransactionParticipantReq> participants) {

    public Instant resolveOccurredAt() {
        if (occurredAt != null) {
            return occurredAt;
        }
        if (date != null) {
            return date.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        }
        return Instant.now();
    }
}

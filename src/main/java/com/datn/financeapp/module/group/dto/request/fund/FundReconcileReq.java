package com.datn.financeapp.module.group.dto.request.fund;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 *
 * @param actualBalance   tiền thực tế
 * @param reason          lý do
 * @param excludedUserIds những người không tham gia
 */
public record FundReconcileReq(
        @NotNull(message = "Số dư thực tế không được để trống.") Long actualBalance,
        @Size(max = 255, message = "Lý do không được vượt quá 255 ký tự.") String reason,
        List<UUID> excludedUserIds) {
}

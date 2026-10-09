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
        @NotNull Long actualBalance,
        @Size(max = 255) String reason,
        List<UUID> excludedUserIds) {
}

package com.datn.financeapp.group.dto.request.fund;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 *
 * @param actualBalance   tiền thực tế
 * @param date            ngày đối chiếu
 * @param note            lý do
 * @param excludedUserIds những người không tham gia
 */
public record FundReconcileReq(
        @NotNull Long actualBalance,
        @NotNull LocalDate date,
        @Size(max = 255) String note,
        List<UUID> excludedUserIds) {
}

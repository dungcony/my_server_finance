package com.datn.financeapp.goal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Body của {@code POST /goals} (api/09 mục B2).
 *
 * <p>{@code initialAmount} là TRƯỜNG REQUEST, không phải cột lưu trữ — {@code savings_goals}
 * không có cột nào tên như vậy trong schema V4. Xem {@code GoalService.create} để biết cách xử lý.
 */
public record CreateGoalRequest(
        @NotBlank @Size(min = 1, max = 100) String name,
        @NotNull @Positive Long targetAmount,
        LocalDate targetDate,
        UUID walletId,
        UUID iconId,
        Long initialAmount) {}

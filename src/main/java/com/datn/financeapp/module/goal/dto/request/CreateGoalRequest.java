package com.datn.financeapp.module.goal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Yêu cầu tạo mới mục tiêu tiết kiệm.
 *
 * @param name          Tên mục tiêu tiết kiệm (tối đa 100 ký tự)
 * @param targetAmount  Số tiền mục tiêu cần tiết kiệm (Đơn vị: VND)
 * @param targetDate    Ngày dự kiến hoàn thành mục tiêu (tùy chọn)
 * @param walletId      ID ví tiền áp dụng (tùy chọn)
 * @param iconId        ID biểu tượng đại diện (tùy chọn)
 * @param initialAmount Số tiền nộp ban đầu khi tạo mục tiêu (Đơn vị: VND; tùy chọn)
 */
public record CreateGoalRequest(
        @NotBlank @Size(min = 1, max = 100) String name,
        @NotNull @Positive Long targetAmount,
        LocalDate targetDate,
        UUID walletId,
        UUID iconId,
        Long initialAmount) {
}

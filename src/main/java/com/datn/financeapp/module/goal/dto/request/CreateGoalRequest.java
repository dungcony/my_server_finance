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
 * @param name              Tên mục tiêu tiết kiệm (tối đa 100 ký tự)
 * @param targetAmount      Số tiền mục tiêu cần tiết kiệm (Đơn vị: VND)
 * @param targetDate        Ngày dự kiến hoàn thành mục tiêu (tùy chọn)
 * @param walletId          ID ví tiền áp dụng (tùy chọn, nếu muốn gắn vào ví đã có)
 * @param iconId            ID biểu tượng đại diện (tùy chọn)
 * @param initialAmount     Số tiền nộp ban đầu khi tạo mục tiêu (Đơn vị: VND; tùy chọn)
 * @param manageWithWallet  Tự động tạo một ví mới loại SAVINGS mang tên mục tiêu này (tùy chọn)
 */
public record CreateGoalRequest(
        @NotBlank(message = "Tên mục tiêu không được để trống.")
        @Size(min = 1, max = 100, message = "Tên mục tiêu phải từ 1 đến 100 ký tự.")
        String name,

        @NotNull(message = "Số tiền mục tiêu không được để trống.")
        @Positive(message = "Số tiền mục tiêu phải lớn hơn 0.")
        Long targetAmount,

        LocalDate targetDate,
        UUID walletId,
        UUID iconId,
        Long initialAmount,
        Boolean manageWithWallet) {
}

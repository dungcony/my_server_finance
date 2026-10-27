package com.datn.financeapp.module.goal.dto.request;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

/**
 * Yêu cầu tạo mới mục tiêu tiết kiệm.
 *
 * @param name          Tên mục tiêu tiết kiệm (tối đa 100 ký tự)
 * @param targetAmount  Số tiền mục tiêu cần tiết kiệm (Đơn vị: VND)
 * @param targetDate    Ngày dự kiến hoàn thành mục tiêu (tùy chọn)
 * @param iconCode      Mã biểu tượng đại diện (tùy chọn)
 * @param initialAmount Số tiền nộp ban đầu khi tạo mục tiêu (Đơn vị: VND; tùy chọn)
 * @param note          Ghi chú nơi cất giữ tiền (ví dụ: "Nuôi heo đất trong tủ", "Gửi sổ tiết kiệm Agribank"...)
 */
public record SavingGoalCreateReq(
        @NotBlank(message = "Tên mục tiêu không được để trống.")
        @Size(min = 1, max = 100, message = "Tên mục tiêu phải từ 1 đến 100 ký tự.")
        String name,

        @NotNull(message = "Số tiền mục tiêu không được để trống.")
        @Positive(message = "Số tiền mục tiêu phải lớn hơn 0.")
        Long targetAmount,

        @FutureOrPresent(message = "ngày dự kiến không được ở quá khứ")
        LocalDate targetDate,

        @Size(max = 50, message = "Mã biểu tượng tối đa 50 ký tự.")
        String iconCode,

        @PositiveOrZero(message = "Số tiền nộp ban đầu không được âm.")
        Long initialAmount,

        @Size(max = 255, message = "Ghi chú tối đa 255 ký tự.")
        String note) {

    // Gán giá trị mặc định là 0L nếu client không truyền hoặc truyền null
    public SavingGoalCreateReq {
        if (initialAmount == null) {
            initialAmount = 0L;
        }
    }
}

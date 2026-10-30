package com.datn.financeapp.module.goal.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Yêu cầu nộp tiền vào mục tiêu tiết kiệm.
 *
 * @param amount          Số tiền nộp thêm (Đơn vị: VND)
 * @param contributedDate Ngày thực hiện nạp tiền (mặc định: ngày hiện tại)
 * @param note            Ghi chú nạp tiền
 */
public record ContributionGoalCreateReq(
        @NotNull(message = "Số tiền không được để trống.")
        @Positive(message = "Số tiền nạp phải lớn hơn 0.")
        Long amount,

        LocalDate contributedDate,

        @Size(max = 255, message = "Ghi chú tối đa 255 ký tự.")
        String note) {

    public ContributionGoalCreateReq withdraw() {
        return new ContributionGoalCreateReq(-amount, contributedDate, note);
    }
}

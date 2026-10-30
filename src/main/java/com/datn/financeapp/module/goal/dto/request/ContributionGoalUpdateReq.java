package com.datn.financeapp.module.goal.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Yêu cầu chỉnh sửa đợt đóng góp tiết kiệm khi người dùng ghi nhận nhầm.
 *
 * @param amount          Số tiền mới (Đơn vị: VND)
 * @param contributedDate Ngày thực hiện mới
 * @param note            Ghi chú mới
 */
public record ContributionGoalUpdateReq(
        @Positive(message = "Số tiền phải lớn hơn 0.")
        Long amount,

        LocalDate contributedDate,

        @Size(max = 255, message = "Ghi chú tối đa 255 ký tự.")
        String note) {
}

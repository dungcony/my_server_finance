package com.datn.financeapp.module.goal.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Yêu cầu nộp tiền hoặc rút tiền khỏi mục tiêu tiết kiệm.
 *
 * @param amount            Số tiền đóng góp hoặc rút (Đơn vị: VND; dương = nộp, âm = rút)
 * @param contributedDate   Ngày thực hiện giao dịch (mặc định: ngày hiện tại)
 * @param sourceWalletId    ID ví tiền trích/nhận tiền (tùy chọn)
 * @param createTransaction {@code true} để tự động tạo giao dịch trong ví (mặc định: true)
 * @param note              Ghi chú đóng góp
 */
public record CreateContributionRequest(
        @NotNull(message = "Số tiền không được để trống.")
        @Positive(message = "Số tiền phải lớn hơn 0.")
        Long amount,

        LocalDate contributedDate,
        UUID sourceWalletId,
        Boolean createTransaction,
        String note) {
}

package com.datn.financeapp.module.goal.dto.request;

import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO nhận tiêu chí lọc lịch sử nạp tiền và rút tiền của mục tiêu tiết kiệm.
 *
 * @param isWithdrawal        Lọc theo loại giao dịch (true: chỉ khoản rút tiền, false: chỉ khoản nạp tiền, null: tất cả)
 * @param minAmount           Số tiền giao dịch tối thiểu (Đơn vị: VND; tùy chọn)
 * @param maxAmount           Số tiền giao dịch tối đa (Đơn vị: VND; tùy chọn)
 * @param contributedDateFrom Ngày thực hiện giao dịch từ ngày (tùy chọn)
 * @param contributedDateTo   Ngày thực hiện giao dịch đến ngày (tùy chọn)
 */
public record ContributionGoalFilterReq(
        Boolean isWithdrawal,
        Long minAmount,
        Long maxAmount,
        LocalDate contributedDateFrom,
        LocalDate contributedDateTo) {
}
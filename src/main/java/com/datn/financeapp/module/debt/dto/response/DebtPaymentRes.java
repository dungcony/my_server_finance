package com.datn.financeapp.module.debt.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO phản hồi thông tin chi tiết của một đợt thanh toán nợ (debt_payments).
 *
 * @param id        Khóa chính của đợt thanh toán
 * @param debtId    ID khoản nợ cha
 * @param amount    Số tiền thanh toán đợt này (VND)
 * @param paidDate  Ngày thanh toán
 * @param note      Ghi chú đợt thanh toán
 * @param createdAt Thời điểm tạo bản ghi
 */
public record DebtPaymentRes(
        UUID id,
        UUID debtId,
        Long amount,
        LocalDate paidDate,
        String note,
        Instant createdAt
) {
}

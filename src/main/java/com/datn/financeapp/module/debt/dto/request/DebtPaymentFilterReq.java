package com.datn.financeapp.module.debt.dto.request;

import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO nhận tiêu chí lọc danh sách đợt thanh toán nợ (debt_payments).
 *
 * @param debtId       ID khoản nợ (tùy chọn nếu filter trong 1 khoản nợ cụ thể hoặc toàn hệ thống cho Admin)
 * @param paidDateFrom Ngày thanh toán từ ngày (tùy chọn)
 * @param paidDateTo   Ngày thanh toán đến ngày (tùy chọn)
 * @param minAmount    Số tiền thanh toán tối thiểu (tùy chọn)
 * @param maxAmount    Số tiền thanh toán tối đa (tùy chọn)
 * @param note         Từ khóa tìm kiếm trong ghi chú (tùy chọn)
 */
public record DebtPaymentFilterReq(
        UUID debtId,
        LocalDate paidDateFrom,
        LocalDate paidDateTo,
        Long minAmount,
        Long maxAmount,
        String note
) {
}

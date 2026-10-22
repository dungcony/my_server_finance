package com.datn.financeapp.module.debt.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Thông tin chi tiết một khoản nợ trong danh sách.
 *
 * @param id               ID khoản nợ
 * @param type             Loại nợ ({@code lending} hoặc {@code borrowing})
 * @param counterpartyName Tên đối tác/người liên quan
 * @param principalAmount  Số tiền gốc (Đơn vị: VND)
 * @param paidAmount       Số tiền đã thanh toán (Đơn vị: VND)
 * @param remainingAmount  Số tiền còn lại (Đơn vị: VND)
 * @param paidRatio        Tỷ lệ đã thanh toán (VD: 0.50 = 50%)
 * @param issuedDate       Ngày phát sinh khoản nợ
 * @param dueDate          Ngày hạn phải thu/trả nợ
 * @param daysRemaining    Số ngày còn lại tới hạn
 * @param isOverdue        Có quá hạn hay không
 * @param status           Trạng thái ({@code active}, {@code completed}, {@code written_off})
 * @param note             Ghi chú
 * @param wallet           Thông tin tóm tắt ví liên quan
 * @param paymentCount     Số lần đã thanh toán
 */
public record DebtListItemResponse(
        UUID id,
        String type,
        String counterpartyName,
        Long principalAmount,
        Long paidAmount,
        Long remainingAmount,
        BigDecimal paidRatio,
        LocalDate issuedDate,
        LocalDate dueDate,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer daysRemaining,
        boolean isOverdue,
        String status,
        String note,
        WalletSummary wallet,
        int paymentCount) {

    /**
     * Tóm tắt ví liên quan.
     *
     * @param id   ID ví
     * @param name Tên ví
     */
    public record WalletSummary(UUID id, String name) {
    }
}

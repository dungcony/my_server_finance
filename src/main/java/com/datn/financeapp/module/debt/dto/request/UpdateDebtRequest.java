package com.datn.financeapp.module.debt.dto.request;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Yêu cầu cập nhật thông tin khoản nợ.
 *
 * @param counterpartyName Tên đối tác/người liên quan (tối đa 100 ký tự)
 * @param dueDate          Ngày hạn trả nợ mới
 * @param note             Ghi chú bổ sung mới
 */
public record UpdateDebtRequest(
        @Size(max = 100, message = "Tên người liên quan tối đa 100 ký tự.") String counterpartyName,
        LocalDate dueDate,
        String note) {
}

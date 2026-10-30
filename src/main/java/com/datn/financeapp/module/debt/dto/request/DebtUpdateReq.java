package com.datn.financeapp.module.debt.dto.request;

import jakarta.validation.constraints.FutureOrPresent;

import java.time.LocalDate;

/**
 * Yêu cầu cập nhật thông tin khoản nợ.
 *
 * @param name
 * @param dueDate Ngày hạn trả nợ mới
 * @param note    Ghi chú bổ sung mới
 */
public record DebtUpdateReq(
                Long totalAmount,
                String name,
                @FutureOrPresent(message = "ngày kết thúc không được ở quá khứ") LocalDate dueDate,
                String note) {
}

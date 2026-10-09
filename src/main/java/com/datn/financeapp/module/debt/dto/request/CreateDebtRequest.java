package com.datn.financeapp.module.debt.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Yêu cầu tạo mới khoản nợ hoặc cho vay.
 *
 * @param type             Loại khoản nợ ({@code lending} cho vay, {@code borrowing} đi vay)
 * @param counterpartyName Tên đối tác/người liên quan (tối đa 100 ký tự)
 * @param principalAmount  Số tiền nợ gốc (Đơn vị: VND)
 * @param walletId         ID ví tiền phát sinh giao dịch nợ
 * @param issuedDate       Ngày phát sinh khoản nợ (mặc định: ngày hiện tại)
 * @param dueDate          Ngày hạn phải trả/thu nợ (tùy chọn)
 * @param note             Ghi chú thêm về khoản nợ
 */
public record CreateDebtRequest(
        @NotBlank(message = "Thiếu loại khoản nợ.")
        @Pattern(regexp = "lending|borrowing", message = "Loại khoản nợ phải là lending hoặc borrowing.")
        String type,
        @NotBlank(message = "Thiếu tên người liên quan.")
        @Size(max = 100, message = "Tên người liên quan tối đa 100 ký tự.")
        String counterpartyName,
        @NotNull(message = "Thiếu số tiền gốc.") @Positive(message = "Số tiền phải lớn hơn 0.")
        Long principalAmount,
        @NotNull(message = "Thiếu ví.") UUID walletId,
        LocalDate issuedDate,
        LocalDate dueDate,
        String note) {
}

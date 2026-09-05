package com.datn.financeapp.debt.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Body {@code POST /debts} (api/08 mục 4). {@code issuedDate} rỗng = hôm nay.
 *
 * <p>KHÔNG có trường {@code categoryId}: danh mục của giao dịch gốc do backend tự tra theo
 * {@code type} ("Cho vay" cho {@code lending}, "Đi vay" cho {@code borrowing}) — client không
 * được chọn, để giao dịch sổ nợ luôn nằm đúng bốn danh mục hệ thống đã tách khỏi chi tiêu
 * sinh hoạt.
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

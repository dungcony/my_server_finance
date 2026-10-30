package com.datn.financeapp.module.debt.dto.request;

import com.datn.financeapp.module.debt.enums.DebtType;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Yêu cầu tạo mới khoản nợ hoặc cho vay.
 *
 * @param type        Loại khoản nợ ({@code lending} cho vay, {@code borrowing} đi vay)
 * @param name
 * @param totalAmount Số tiền nợ gốc (Đơn vị: VND)
 * @param walletId    ID ví tiền phát sinh giao dịch nợ
 * @param issuedDate  Ngày phát sinh khoản nợ (mặc định: ngày hiện tại)
 * @param dueDate     Ngày hạn phải trả/thu nợ (tùy chọn)
 * @param note        Ghi chú thêm về khoản nợ
 */
public record DebtCreateReq(
        String name,
        @NotNull(message = "Thiếu loại khoản nợ.")
        DebtType type,
        @NotNull(message = "Thiếu số tiền gốc.")
        @Positive(message = "Số tiền phải lớn hơn 0.")
        Long totalAmount,
        @NotNull(message = "Thiếu ví.")
        UUID walletId,
        @NotNull(message = "ngày bắt đầu k được để trông")
        @PastOrPresent(message = "ngày bắt đầu k được ở tương lai")
        LocalDate issuedDate,
        @FutureOrPresent(message = "ngày kết thúc không được ở quá khứ")
        LocalDate dueDate,
        String note
) {
}

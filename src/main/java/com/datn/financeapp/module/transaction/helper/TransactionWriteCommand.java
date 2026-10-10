package com.datn.financeapp.module.transaction.helper;

import com.datn.financeapp.module.transaction.enums.TransactionSource;
import com.datn.financeapp.module.transaction.enums.TransactionType;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Record chứa thông tin lệnh ghi giao dịch dùng cho {@link TransactionWriter#write}.
 *
 * @param id          ID giao dịch (null để tự sinh)
 * @param userId      ID người dùng
 * @param walletId    ID ví
 * @param categoryId  ID danh mục
 * @param type        loại giao dịch (EXPENSE, INCOME, LOAN)
 * @param amount      số tiền giao dịch
 * @param date        ngày thực hiện giao dịch
 * @param note        ghi chú
 * @param name        tên hiển thị
 * @param source      nguồn giao dịch (MANUAL, AI, RECURRING)
 * @param recurringId ID giao dịch định kỳ tham chiếu (nếu có)
 * @param receiptUrl  URL hình ảnh hóa đơn (nếu có)
 */
public record TransactionWriteCommand(
        UUID id,
        UUID userId,
        UUID walletId,
        UUID categoryId,
        TransactionType type,
        long amount,
        LocalDate date,
        String note,
        String name,
        TransactionSource source,
        UUID recurringId,
        String receiptUrl) {
}

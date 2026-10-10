package com.datn.financeapp.module.transaction.helper;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Record chứa thông tin lệnh ghi giao dịch dùng cho {@link TransactionWriter#write}.
 *
 * @param id                  ID giao dịch (null để tự sinh)
 * @param userId              ID người dùng
 * @param walletId            ID ví nguồn
 * @param destinationWalletId ID ví đích (dành cho chuyển tiền)
 * @param categoryId          ID danh mục (null cho chuyển tiền)
 * @param type                loại giao dịch (income, expense, transfer)
 * @param amount              số tiền giao dịch
 * @param date                ngày thực hiện giao dịch
 * @param note                ghi chú
 * @param displayName         tên hiển thị
 * @param source              nguồn giao dịch (manual, auto, bot)
 * @param countsInReport      có tính vào báo cáo hay không
 * @param recurringId         ID giao dịch định kỳ tham chiếu (nếu có)
 * @param draftId             ID bản nháp tham chiếu (nếu có)
 * @param receiptUrl          URL hình ảnh hóa đơn (nếu có)
 */
public record TransactionWriteCommand(
        UUID id,
        UUID userId,
        UUID walletId,
        UUID destinationWalletId,
        UUID categoryId,
        String type,
        long amount,
        LocalDate date,
        String note,
        String displayName,
        String source,
        boolean countsInReport,
        UUID recurringId,
        UUID draftId,
        String receiptUrl) {
}

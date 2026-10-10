package com.datn.financeapp.module.transaction.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Phản hồi chi tiết giao dịch định kỳ kèm lịch sử giao dịch đã được phát sinh.
 *
 * @param recurring thông tin chi tiết của giao dịch định kỳ
 * @param history   danh sách các giao dịch thực tế đã phát sinh từ lịch lặp này
 */
public record RecurringDetailResponse(RecurringListItemResponse recurring, List<GeneratedTransaction> history) {

    /**
     * Thông tin tóm tắt giao dịch đã phát sinh.
     *
     * @param id     ID giao dịch
     * @param date   ngày ghi nhận giao dịch
     * @param amount số tiền giao dịch
     * @param type   loại giao dịch (INCOME/EXPENSE)
     * @param source nguồn gốc phát sinh giao dịch
     */
    public record GeneratedTransaction(UUID id, LocalDate date, Long amount, String type, String source) {
    }
}

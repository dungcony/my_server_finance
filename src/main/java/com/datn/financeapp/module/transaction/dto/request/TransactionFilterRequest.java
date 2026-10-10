package com.datn.financeapp.module.transaction.dto.request;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Tham số lọc dùng chung cho GET /transactions và GET /transactions/by-date.
 *
 * @param fromDate         ngày bắt đầu
 * @param toDate           ngày kết thúc
 * @param period           khoảng thời gian (week/month/quarter/year)
 * @param type             loại giao dịch (EXPENSE, INCOME, LOAN)
 * @param walletId         ID ví
 * @param categoryId       ID danh mục
 * @param source           nguồn giao dịch (MANUAL, AI, RECURRING)
 * @param search           từ khóa tìm kiếm
 * @param minAmount        số tiền tối thiểu
 * @param maxAmount        số tiền tối đa
 */
public record TransactionFilterRequest(
        LocalDate fromDate,
        LocalDate toDate,
        String period,
        String type,
        UUID walletId,
        UUID categoryId,
        String source,
        String search,
        Long minAmount,
        Long maxAmount) {
}

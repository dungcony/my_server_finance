package com.datn.financeapp.module.transaction.dto.request;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Tham số lọc dùng chung cho truy vấn danh sách giao dịch cá nhân.
 *
 * @param from       Thời gian bắt đầu
 * @param to         Thời gian kết thúc
 * @param period     khoảng thời gian (week/month/quarter/year)
 * @param type       loại giao dịch (EXPENSE, INCOME, LOAN)
 * @param walletId   ID ví
 * @param categoryId ID danh mục
 * @param source     nguồn giao dịch (MANUAL, AI, RECURRING)
 * @param search     từ khóa tìm kiếm
 * @param minAmount  số tiền tối thiểu
 * @param maxAmount  số tiền tối đa
 */
public record TxnFilterRequest(
        Instant from,
        Instant to,
        String period,
        String type,
        UUID walletId,
        UUID categoryId,
        String source,
        String search,
        Long minAmount,
        Long maxAmount) {

}

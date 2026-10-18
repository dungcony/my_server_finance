package com.datn.financeapp.module.transaction.dto.request;

import org.springframework.web.bind.annotation.BindParam;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Collection;
import java.util.UUID;

/**
 * Tham số lọc dùng chung cho truy vấn danh sách giao dịch cá nhân.
 *
 * @param from        Thời gian bắt đầu
 * @param to          Thời gian kết thúc
 * @param period      khoảng thời gian (week/month/quarter/year)
 * @param type        loại giao dịch (EXPENSE, INCOME, LOAN)
 * @param walletId    ID ví
 * @param categoryIds IDs danh mục
 * @param search      từ khóa tìm kiếm
 * @param minAmount   số tiền tối thiểu
 * @param maxAmount   số tiền tối đa
 */
public record TxnFilterRequest(
        Instant from,
        Instant to,
        String period,
        String type,
        @BindParam("wallet_id") UUID walletId,
        @BindParam("category_id") Collection<UUID> categoryIds,
        String search,
        @BindParam("min_amount") Long minAmount,
        @BindParam("max_amount") Long maxAmount,
        @BindParam("is_deleted") Boolean isDeleted,
        @BindParam("user_id") UUID userId
) {
    // usser gọi thì isDeleted luôn phải là false
    public TxnFilterRequest withUser(UUID userId) {
        return new TxnFilterRequest(from, to, period, type, walletId, categoryIds, search, minAmount, maxAmount, false, userId);
    }
}

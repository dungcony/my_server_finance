package com.datn.financeapp.module.transaction.dto.request;

import org.springframework.web.bind.annotation.BindParam;

import java.util.UUID;

/**
 * Tham số lọc dùng chung cho truy vấn danh sách giao dịch định kỳ.
 *
 * @param type         loại giao dịch (EXPENSE, INCOME, LOAN)
 * @param frequency    đơn vị lặp (DAY, WEEK, MONTH, QUARTER, YEAR)
 * @param walletId     ID ví
 * @param categoryCode code danh mục
 * @param isEnabled    trạng thái bật/tắt
 * @param search       từ khóa tìm kiếm trong tên hoặc ghi chú
 * @param minAmount    số tiền tối thiểu
 * @param maxAmount    số tiền tối đa
 * @param userId       ID chủ sở hữu (do server gán, không nhận từ client)
 */
public record RecTxnFilterReq(
        String type,
        String frequency,
        @BindParam("wallet_id") UUID walletId,
        @BindParam("category_code") String categoryCode,
        @BindParam("is_enabled") Boolean isEnabled,
        String search,
        @BindParam("min_amount") Long minAmount,
        @BindParam("max_amount") Long maxAmount,
        @BindParam("user_id") UUID userId
) {
    // user gọi thì luôn phải bị giới hạn theo userId của chính mình
    public RecTxnFilterReq withUser(UUID userId) {
        return new RecTxnFilterReq(type, frequency, walletId, categoryCode, isEnabled, search, minAmount, maxAmount, userId);
    }
}

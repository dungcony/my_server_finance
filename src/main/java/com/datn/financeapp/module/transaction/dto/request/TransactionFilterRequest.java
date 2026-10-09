package com.datn.financeapp.module.transaction.dto.request;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Tham số lọc dùng chung cho GET /transactions và GET /transactions/by-date.
 *
 * @param fromDate         ngày bắt đầu
 * @param toDate           ngày kết thúc
 * @param period           khoảng thời gian (week/month/quarter/year)
 * @param type             loại giao dịch
 * @param walletId         ID ví
 * @param categoryId       ID danh mục
 * @param source           nguồn giao dịch
 * @param countsInReport   có tính vào báo cáo hay không
 * @param search           từ khóa tìm kiếm
 * @param minAmount        số tiền tối thiểu
 * @param maxAmount        số tiền tối đa
 * @param includeTransfers bao gồm giao dịch chuyển tiền hay không
 */
public record TransactionFilterRequest(
        LocalDate fromDate,
        LocalDate toDate,
        String period,
        String type,
        UUID walletId,
        UUID categoryId,
        String source,
        Boolean countsInReport,
        String search,
        Long minAmount,
        Long maxAmount,
        Boolean includeTransfers) {

    public boolean includeTransfersOrDefault() {
        return includeTransfers == null || includeTransfers;
    }
}

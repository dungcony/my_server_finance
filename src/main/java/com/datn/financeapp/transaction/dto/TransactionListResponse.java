package com.datn.financeapp.transaction.dto;

import com.datn.financeapp.common.response.PageMeta;
import java.util.List;

/**
 * Khung phản hồi riêng cho GET /transactions — {@code ApiResponse<T>} dùng chung của dự án chỉ
 * có {@code {success, data}}, không mang {@code pagination}/{@code summary} ở CÙNG cấp JSON với
 * {@code data} (api/00-QUY-UOC-CHUNG.md mục 4.2 chỉ định nghĩa {@code pagination}; {@code
 * summary} là mở rộng riêng của api/04-GIAO-DICH.md mục 1). KHÔNG sửa {@code ApiResponse} dùng
 * chung — sẽ ảnh hưởng mọi endpoint khác.
 */
public record TransactionListResponse(
        boolean success,
        List<TransactionListItemResponse> data,
        PageMeta pagination,
        TransactionSummaryResponse summary) {

    public static TransactionListResponse of(
            List<TransactionListItemResponse> data, PageMeta pagination, TransactionSummaryResponse summary) {
        return new TransactionListResponse(true, data, pagination, summary);
    }
}

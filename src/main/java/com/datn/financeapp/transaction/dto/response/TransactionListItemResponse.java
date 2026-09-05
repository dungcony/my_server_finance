package com.datn.financeapp.transaction.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Một phần tử của GET /transactions và GET /transactions/by-date (api/04-GIAO-DICH.md mục 1/2).
 * Khác {@link TransactionResponse} (dùng cho phản hồi create/update/duplicate) ở chỗ {@code
 * category} mang đủ {@code icon}/{@code color}/{@code parent} để màn Sổ giao dịch vẽ trực tiếp,
 * không cần gọi thêm API danh mục (chống N+1 ở tầng client).
 */
public record TransactionListItemResponse(
        UUID id,
        String type,
        Long amount,
        LocalDate date,
        String displayName,
        String note,
        String source,
        /**
         * Khoản này có được tính vào báo cáo thu-chi không (api/04 mục 4, cột
         * {@code transactions.counts_in_report} của V8).
         *
         * <p>Trước đây chỉ NHẬN được lúc tạo mà không TRẢ LẠI trong response, nên client ghi cờ
         * xuống rồi không đọc lại được — mở màn Sửa là mất giá trị đã đặt (FIX-07, đợt test
         * 02/09/2026).
         */
        Boolean countsInReport,
        WalletRef wallet,
        WalletRef destinationWallet,
        CategoryRef category,
        String receiptUrl,
        UUID recurringId,
        UUID draftId,
        Instant createdAt,
        Instant updatedAt) {

    public record WalletRef(UUID id, String name, String type) {}

    public record CategoryRef(
            UUID id, String name, String type, IconRef icon, String color, ParentRef parent) {}

    public record IconRef(String code, String pathData) {}

    public record ParentRef(UUID id, String name) {}
}

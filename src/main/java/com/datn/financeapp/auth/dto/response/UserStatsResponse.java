package com.datn.financeapp.auth.dto.response;

/**
 * Phần "stats" lồng trong GET /auth/me (api/01-XAC-THUC.md mục 5). Phase 1 chưa có module
 * transaction/group nghiệp vụ — transactionCount/groupCount đếm trực tiếp trên bảng schema đã
 * có sẵn từ V1/V2, luôn trả số thật (0 nếu chưa có dữ liệu), không lỗi, không null.
 */
public record UserStatsResponse(long walletCount, long transactionCount, long groupCount) {
}

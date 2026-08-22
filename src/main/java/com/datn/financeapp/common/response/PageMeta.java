package com.datn.financeapp.common.response;

/**
 * Metadata phân trang, theo api/00-QUY-UOC-CHUNG.md mục 4.2.
 * Dựng sẵn ở Phase 1 (CORE-02) cho Phase 2 dùng lại — chưa có endpoint phân trang ở Phase 1.
 */
public record PageMeta(int page, int pageSize, long totalItems, int totalPages) {}

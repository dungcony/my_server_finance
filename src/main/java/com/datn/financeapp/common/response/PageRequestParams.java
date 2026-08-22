package com.datn.financeapp.common.response;

/**
 * Tham số phân trang/sắp xếp chuẩn hoá, theo api/00-QUY-UOC-CHUNG.md mục 7.
 * Dựng sẵn ở Phase 1 (CORE-02) cho Phase 2 dùng lại — chưa có endpoint phân trang ở Phase 1.
 */
public record PageRequestParams(int page, int pageSize, String sortBy, String sortOrder) {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    public static PageRequestParams of(Integer page, Integer pageSize, String sortBy, String sortOrder) {
        int p = (page == null || page < 1) ? DEFAULT_PAGE : page;
        int ps = (pageSize == null || pageSize < 1) ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        String order = "asc".equalsIgnoreCase(sortOrder) ? "asc" : "desc";
        return new PageRequestParams(p, ps, sortBy, order);
    }
}

package com.datn.financeapp.common.response;

/**
 * Tham số phân trang/sắp xếp chuẩn hoá.
 *
 * @param page      số trang
 * @param pageSize  số lượng phần tử trên trang
 * @param sortBy    trường dùng để sắp xếp
 * @param sortOrder thứ tự sắp xếp (asc hoặc desc)
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

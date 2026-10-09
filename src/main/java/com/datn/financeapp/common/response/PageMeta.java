package com.datn.financeapp.common.response;

/**
 * Metadata phân trang theo chuẩn chung của hệ thống.
 *
 * @param page       số trang hiện tại
 * @param pageSize   số phần tử trên mỗi trang
 * @param totalItems tổng số phần tử
 * @param totalPages tổng số trang
 */
public record PageMeta(int page, int pageSize, long totalItems, int totalPages) {
}

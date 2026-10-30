package com.datn.financeapp.common.response;

import org.springframework.data.domain.Page;

/**
 * Metadata phân trang theo chuẩn chung của hệ thống.
 *
 * @param page       số trang hiện tại
 * @param pageSize   số phần tử trên mỗi trang
 * @param totalItems tổng số phần tử
 * @param totalPages tổng số trang
 */
public record PageMeta(int page, int pageSize, long totalItems, int totalPages) {

    public static PageMeta from(Page<?> page) {
        if (page == null) {
            return null;
        }
        return new PageMeta(
                page.getNumber() + 1,       // Spring Data là 0-indexed, API trả về 1-indexed
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}

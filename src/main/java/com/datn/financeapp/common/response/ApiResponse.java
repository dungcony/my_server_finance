package com.datn.financeapp.common.response;

/**
 * Khung phản hồi thành công thống nhất cho toàn bộ API, theo api/00-QUY-UOC-CHUNG.md mục 4.1.
 */
public record ApiResponse<T>(boolean success, T data) {

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(true, data);
    }
}

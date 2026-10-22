package com.datn.financeapp.common.response;

/**
 * Khung phản hồi thành công thống nhất cho toàn bộ API.
 *
 * @param <T>     kiểu dữ liệu của data
 * @param success trạng thái thành công
 * @param data    dữ liệu phản hồi
 * @param msg     thông điệp kèm theo
 */
public record ApiResponse<T>(boolean success, T data, String msg) {

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(true, data, "");
    }

    public static <T> ApiResponse<T> of(T data, String msg) {
        return new ApiResponse<>(true, data, msg);
    }
}

package com.datn.financeapp.auth.helper;

/**
 * Phần dữ liệu duy nhất luồng đăng nhập cần lấy ra từ token.
 *
 * @param googleId trường {@code sub} — định danh KHÔNG đổi kể cả khi người dùng đổi email
 *                 bên Google, nên đây mới là thứ đáng lưu vào {@code users.google_id}
 * @param email    hộp thư Google đã xác thực
 * @param name     tên hiển thị, có thể rỗng — chỉ dùng lúc TẠO tài khoản, không đồng bộ lại
 *                 ở những lần đăng nhập sau (prd/01 mục 9.4b)
 */
public record GoogleUserInfo(
        String googleId,
        String email,
        String name) {
}

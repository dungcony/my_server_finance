package com.datn.financeapp.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/**
 * Phần "user" trong phản hồi register/login (api/01-XAC-THUC.md mục 1).
 *
 * <p>KHÔNG trả {@code is_blocked}/{@code is_deleted}: hai cột đó luôn {@code false} với bất kỳ
 * ai gọi được API (tài khoản khoá/xoá không đăng nhập nổi — B3 chặn ở cả bốn cửa), trả ra chỉ
 * khiến app tưởng mình cần xử lý chúng.
 */
public record UserSummaryResponse(
        UUID id,
        String email,
        String username,
        String avatarUrl,
        String plan,
        /**
         * Đã xác thực email chưa. Tên field bắt đầu bằng "is" nên Jackson mặc định sinh khoá
         * JSON "confirm" — chỉ định tường minh để giữ đúng {@code is_confirm} như api/01 chốt.
         */
        @JsonProperty("is_confirm") boolean isConfirm,
        /** {@code USER} / {@code ADMIN}. App nhận nhưng TUYỆT ĐỐI không dùng để ẩn/hiện chức năng. */
        String role,
        /**
         * D4: {@code password_hash} khác rỗng. App dùng để ẩn nút Đổi mật khẩu với tài khoản
         * Google thuần. KHÔNG thay được bằng {@code is_confirm}: tài khoản đã liên kết cũng
         * {@code true} mà vẫn đổi mật khẩu được (api/01 mục 13).
         */
        @JsonProperty("has_password") boolean hasPassword,
        /** D4: {@code google_id} khác rỗng. Màn Tài khoản dùng để hiện "Đã liên kết Google". */
        @JsonProperty("google_linked") boolean googleLinked,
        Instant createdAt) {
}

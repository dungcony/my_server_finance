package com.datn.financeapp.common.security;

import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Helper lấy userId hiện tại từ SecurityContextHolder — dùng lại ở mọi Service/Aspect
 * cần biết "current user" (CORE-05: kiểm tra quyền ngay trong câu truy vấn).
 */
public final class SecurityContextUtil {

    private SecurityContextUtil() {
    }

    public static UUID currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return UUID.fromString(principal.toString());
    }

    /**
     * Level của role MẠNH NHẤT user hiện tại đang giữ, lấy từ claim "roles_level_top" trong
     * JWT — CHỈ dùng để đọc/lọc (vd ẩn bớt user cấp cao hơn khỏi danh sách), KHÔNG dùng để
     * chặn hành động ghi vì giá trị này có thể lệch so với DB tới khi token hết hạn (xem
     * javadoc JwtService.generateAccessToken).
     *
     * Quy ước "số nhỏ = quyền cao": fallback khi thiếu claim (token cũ/không có details)
     * PHẢI là Integer.MAX_VALUE (yếu nhất) — dùng 0 sẽ bị hiểu nhầm là mạnh nhất hệ thống,
     * mở lỗ hổng cho ai cầm token cũ thấy hết mọi user kể cả admin.
     */
    public static int currentLevel() {
        Object details = SecurityContextHolder.getContext().getAuthentication().getDetails();
        return details instanceof Integer level ? level : Integer.MAX_VALUE;
    }
}

package com.datn.financeapp.common.security;

import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Helper lấy userId hiện tại từ SecurityContextHolder — dùng lại ở mọi Service/Aspect
 * cần biết "current user" (CORE-05: kiểm tra quyền ngay trong câu truy vấn).
 */
public final class SecurityContextUtil {

    private SecurityContextUtil() {}

    public static UUID currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return UUID.fromString(principal.toString());
    }
}

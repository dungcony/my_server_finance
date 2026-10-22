package com.datn.financeapp.module.auth.helper;

import jakarta.servlet.http.HttpServletRequest;

public record ClientInfo(
        String ipAddress,
        String userAgent
) {
    public static ClientInfo from(HttpServletRequest httpReq) {
        return new ClientInfo(
                httpReq.getRemoteAddr(),
                httpReq.getHeader("User-Agent")
        );
    }
}
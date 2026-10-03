package com.datn.financeapp.auth.dto.response;

public record TokenRes(
        String access,
        String refresh,
        long expiresIn
) {
}

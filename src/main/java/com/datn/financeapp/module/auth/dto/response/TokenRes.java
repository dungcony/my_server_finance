package com.datn.financeapp.module.auth.dto.response;

public record TokenRes(
        String access,
        String refresh,
        long expiresIn
) {
}

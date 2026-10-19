package com.datn.financeapp.module.user.dto.request;

import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(

        @Size(max = 100, message = "Tên không được vượt quá 100 ký tự.") String firstName,
        @Size(max = 100, message = "Họ không được vượt quá 100 ký tự.") String lastName,
        String avatarUrl) {
}

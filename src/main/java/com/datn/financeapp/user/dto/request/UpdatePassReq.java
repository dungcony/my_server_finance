package com.datn.financeapp.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePassReq(
        @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại.") String oldPassword,
        @NotBlank(message = "Vui lòng nhập mật khẩu mới.")
        @Size(min = 8, message = "Mật khẩu mới phải từ 8 ký tự.") String newPassword) {
}

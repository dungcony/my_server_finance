package com.datn.financeapp.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body của POST /auth/register (api/01-XAC-THUC.md mục 1).
 *
 * <p>Mật khẩu CHỈ đòi độ dài tối thiểu — điều kiện "phải có ít nhất một chữ và một số" đã bỏ
 * (prd/01-XAC-THUC-VA-TAI-KHOAN.md mục 6.10). Nó không làm mật khẩu mạnh lên bao nhiêu, người
 * dùng chỉ thêm số 1 vào cuối, nhưng lại chặn những mật khẩu dài toàn chữ vốn khó đoán hơn.
 */
public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, message = "Mật khẩu phải từ 8 ký tự.") String password,
        @NotBlank @Size(max = 100) String username) {
}

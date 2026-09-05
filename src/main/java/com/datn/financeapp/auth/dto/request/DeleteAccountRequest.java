package com.datn.financeapp.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * DELETE /auth/account (api/01-XAC-THUC.md mục 10).
 *
 * <p>Chỉ có mật khẩu hiện tại: danh tính lấy từ JWT chứ không nhận từ body — endpoint bắt buộc
 * xác thực nên user_id luôn có sẵn và đáng tin hơn một trường do client gửi lên.
 */
public record DeleteAccountRequest(@NotBlank String password) {}

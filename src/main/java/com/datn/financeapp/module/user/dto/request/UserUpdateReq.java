package com.datn.financeapp.module.user.dto.request;

import com.datn.financeapp.module.user.enums.UserPlan;
import com.datn.financeapp.module.user.enums.UserStatus;
import jakarta.validation.constraints.Size;

public record UserUpdateReq(
        String password,
        UserStatus status,
        UserPlan plan,
        @Size(max = 100, message = "Tên không được vượt quá 100 ký tự.") String firstName,
        @Size(max = 100, message = "Họ không được vượt quá 100 ký tự.") String lastName,
        String avatarUrl,
        Boolean isDeleted
) {

    // Khôi phục tài khoản đã xoá mềm khi đăng ký lại: đặt lại mật khẩu, về chờ xác minh, gói FREE
    public static UserUpdateReq forRestore(String passwordHash) {
        return new UserUpdateReq(passwordHash, UserStatus.PENDING_VERIFY, UserPlan.FREE,
                null, null, null, Boolean.FALSE);
    }
}
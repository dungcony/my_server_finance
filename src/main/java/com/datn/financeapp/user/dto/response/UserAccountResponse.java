package com.datn.financeapp.user.dto.response;

import java.util.List;
import java.util.UUID;

import com.datn.financeapp.user.enums.UserPlan;
import com.datn.financeapp.user.enums.UserStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * DTO nội bộ phục vụ xác thực giữa UserAccountService và AuthService.
 * Tránh trả trực tiếp User entity ra ngoài module user.
 */
public record UserAccountResponse(
        UUID id,
        String email,
        @JsonIgnore String password,
        UserPlan plan,
        UserStatus status,
        List<RoleResponse> roles,
        @JsonIgnore boolean isDeleted,
        @JsonIgnore String googleId) {

    @JsonIgnore
    public boolean isBlocked() {
        return status == UserStatus.BLOCKED;
    }

    @JsonIgnore
    public boolean notConfirm() {
        return status == UserStatus.PENDING_VERIFY;
    }


    @JsonIgnore
    public boolean isConfirm() {
        return status == UserStatus.ACTIVE;
    }

    @JsonIgnore
    public boolean hasPassword() {
        return password != null;
    }

    @JsonIgnore
    public boolean googleLinked() {
        return googleId != null;
    }
}

package com.datn.financeapp.module.user.dto.response;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.datn.financeapp.module.user.enums.UserPlan;
import com.datn.financeapp.module.user.enums.UserStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO nội bộ phục vụ xác thực giữa UserAccountService và AuthService.
 * Tránh trả trực tiếp User entity ra ngoài module user.
 */
public record UserRes(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String avatarUrl,
        UserPlan plan,
        Instant planExpiresAt,
        UserStatus status,
        List<RoleResponse> roles,
        @JsonIgnore String password,
        @JsonIgnore String googleId,
        @JsonIgnore boolean isDeleted) {

    @JsonIgnore
    public boolean isBlocked() {
        return status == UserStatus.BLOCKED;
    }

    // giữ chữ ký cũ cho các chỗ dựng UserRes không quan tâm avatar và hạn gói
    public UserRes(UUID id, String email, String firstName, String lastName, UserPlan plan, UserStatus status,
                   List<RoleResponse> roles, String password, String googleId, boolean isDeleted) {
        this(id, email, firstName, lastName, null, plan, null, status, roles, password, googleId, isDeleted);
    }

    // giữ chữ ký cũ cho các chỗ dựng UserRes không quan tâm hạn gói
    public UserRes(UUID id, String email, String firstName, String lastName, String avatarUrl, UserPlan plan, UserStatus status,
                   List<RoleResponse> roles, String password, String googleId, boolean isDeleted) {
        this(id, email, firstName, lastName, avatarUrl, plan, null, status, roles, password, googleId, isDeleted);
    }

    public UserRes(String firstName, UserStatus status, boolean isDeleted) {
        this(
                null,
                null,
                firstName,
                null,
                null,
                null,
                null,
                status,
                null,
                null,
                null,
                isDeleted
        );
    }

    // bỏ qua phần bị thiếu để tài khoản chỉ có tên thay thế không bị in chữ null
    public String fullName() {
        return Stream.of(firstName, lastName)
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(" "));
    }

    @JsonIgnore
    public boolean isConfirm() {
        return status == UserStatus.ACTIVE;
    }

    @JsonProperty
    public boolean hasPassword() {
        return password != null;
    }

    @JsonIgnore
    public boolean googleLinked() {
        return googleId != null;
    }

    // trích xuất danh sách authority (role name + permission name) từ roles đã load sẵn
    @JsonIgnore
    public List<String> getAuthorities() {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (RoleResponse role : roles) {
            result.add(role.name().name());
            if (role.permissions() != null) {
                for (PermissionResponse p : role.permissions()) {
                    result.add(p.name().name());
                }
            }
        }
        return result.stream().distinct().toList();
    }

    // lấy level nhỏ nhất (role mạnh nhất) từ roles đã load sẵn
    @JsonIgnore
    public int getTopRoleLevel() {
        if (roles == null || roles.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        int min = Integer.MAX_VALUE;
        for (RoleResponse role : roles) {
            if (role.level() != null && role.level() < min) {
                min = role.level();
            }
        }
        return min;
    }
}

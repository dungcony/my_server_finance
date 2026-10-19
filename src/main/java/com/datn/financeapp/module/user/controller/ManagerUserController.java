package com.datn.financeapp.module.user.controller;

import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.user.dto.request.BlockUserRequest;
import com.datn.financeapp.module.user.dto.request.UserUnlockRequest;
import com.datn.financeapp.module.user.dto.request.UserRoleUpdateReq;
import com.datn.financeapp.module.user.service.ManagerUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin/user")
@RequiredArgsConstructor
public class ManagerUserController {

    private final ManagerUserService adminUserService;

    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).USERS_READ.name())")
    public ApiResponse<?> findUserByid(
            @PathVariable UUID userId) {

        return ApiResponse.of(adminUserService.findByUserId(userId));
    }

    @GetMapping("/all")
    @PreAuthorize("hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).USERS_READ.name())")
    public ApiResponse<?> findAllUser() {
        return ApiResponse.of(adminUserService.findAllUser());
    }

    @PatchMapping("/lock")
    @PreAuthorize("hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).USERS_UPDATE.name())")
    public ApiResponse<Void> blockUser(
            @Valid @RequestBody BlockUserRequest req) {
        adminUserService.lockUser(SecurityContextUtil.currentUserId(), req);
        return ApiResponse.of(null, "Khóa tài khoản người dùng thành công");
    }

    @PatchMapping("/unlock")
    @PreAuthorize("hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).USERS_UPDATE.name())")
    public ApiResponse<Void> unlockUser(
            @Valid @RequestBody UserUnlockRequest req) {
        adminUserService.unlockUser(SecurityContextUtil.currentUserId(), req);
        return ApiResponse.of(null, "Mở khóa tài khoản người dùng thành công");
    }

    @PostMapping("/role")
    @PreAuthorize("hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).USER_ROLE_UPDATE.name())")
    public ApiResponse<Void> addRoleToUser(
            @Valid @RequestBody UserRoleUpdateReq req) {
        adminUserService.addRoleToUser(req);
        return ApiResponse.of(null, "Gán vai trò cho người dùng thành công");
    }

    @DeleteMapping("/role")
    @PreAuthorize("hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).USER_ROLE_DELETE.name())")
    public ApiResponse<Void> removeRoleToUser(
            @Valid @RequestBody UserRoleUpdateReq req) {
        adminUserService.removeRoleToUser(req);
        return ApiResponse.of(null, "Xóa vai trò của người dùng thành công");
    }

    @PostMapping("/undelete/{userId}")
    @PreAuthorize("hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).USERS_DELETE.name())")
    public ApiResponse<?> unDeleteUserById(
            @PathVariable UUID userId
    ) {
        return ApiResponse.of(adminUserService.unDeleteByUserId(userId));
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).USERS_DELETE.name())")
    public ApiResponse<?> deleteUserById(
            @PathVariable UUID userId
    ) {
        return ApiResponse.of(adminUserService.deleteByUserId(userId));
    }
}

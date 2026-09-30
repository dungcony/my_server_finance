package com.datn.financeapp.user.controller;

import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.user.dto.request.BlockUserRequest;
import com.datn.financeapp.user.dto.request.UpdateUserRoleReq;
import com.datn.financeapp.user.service.ManagerAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin/user")
@RequiredArgsConstructor
public class ManagerUserController {

    private final ManagerAccountService adminUserService;

    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority(T(com.datn.financeapp.user.enums.PermissionName).USERS_READ.getValue())")
    public ApiResponse<?> findUserByid(
            @PathVariable UUID userId) {

        return ApiResponse.of(adminUserService.findByUserId(userId));
    }

    @GetMapping("/all")
    @PreAuthorize("hasAuthority(T(com.datn.financeapp.user.enums.PermissionName).USERS_READ.getValue())")
    public ApiResponse<?> findAllUser() {
        return ApiResponse.of(adminUserService.findAllUser());
    }

    @PatchMapping("/block")
    @PreAuthorize("hasAuthority(T(com.datn.financeapp.user.enums.PermissionName).USERS_UPDATE.getValue())")
    public ApiResponse<Void> blockUser(
            @Valid @RequestBody BlockUserRequest req) {
        adminUserService.blockUser(req);
        return ApiResponse.of(null, "Khóa tài khoản người dùng thành công");
    }

    @PostMapping("/role")
    @PreAuthorize("hasAuthority(T(com.datn.financeapp.user.enums.PermissionName).USER_ROLE_UPDATE.getValue())")
    public ApiResponse<Void> addRoleToUser(
            @Valid @RequestBody UpdateUserRoleReq req) {
        adminUserService.addRoleToUser(req);
        return ApiResponse.of(null, "Gán vai trò cho người dùng thành công");
    }

    @DeleteMapping("/role")
    @PreAuthorize("hasAuthority(T(com.datn.financeapp.user.enums.PermissionName).USER_ROLE_DELETE.getValue())")
    public ApiResponse<Void> removeRoleToUser(
            @Valid @RequestBody UpdateUserRoleReq req) {
        adminUserService.removeRoleToUser(req);
        return ApiResponse.of(null, "Xóa vai trò của người dùng thành công");
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority(T(com.datn.financeapp.user.enums.PermissionName).USERS_DELETE.getValue())")
    public ApiResponse<?> deleteUserById(
            @PathVariable UUID userId
    ) {
        return ApiResponse.of(adminUserService.deleteByUserId(userId));
    }
}

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
    @PreAuthorize("hasAuthority('users:read')")
    public ApiResponse<?> findUserByid(
            @PathVariable UUID userId) {

        return ApiResponse.of(adminUserService.findByUserId(userId));
    }

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('users:read')")
    public ApiResponse<?> findAllUser() {
        return ApiResponse.of(adminUserService.findAllUser());
    }

    @PatchMapping("/block")
    @PreAuthorize("hasAuthority('users:update')")
    public ApiResponse<Void> blockUser(
            @Valid @RequestBody BlockUserRequest req) {
        adminUserService.blockUser(req);
        return ApiResponse.of(null, "Khóa tài khoản người dùng thành công");
    }

    @PostMapping("/role")
    @PreAuthorize("hasAuthority('user_role:update')")
    public ApiResponse<Void> addRoleToUser(
            @Valid @RequestBody UpdateUserRoleReq req) {
        adminUserService.addRoleToUser(req);
        return ApiResponse.of(null, "Gán vai trò cho người dùng thành công");
    }

    @DeleteMapping("/role")
    @PreAuthorize("hasAuthority('user_role:delete')")
    public ApiResponse<Void> removeRoleToUser(
            @Valid @RequestBody UpdateUserRoleReq req) {
        adminUserService.removeRoleToUser(req);
        return ApiResponse.of(null, "Xóa vai trò của người dùng thành công");
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority('users:delete')")
    public ApiResponse<?> deleteUserById(
            @PathVariable UUID userId
    ) {
        return ApiResponse.of(adminUserService.deleteByUserId(userId));
    }
}

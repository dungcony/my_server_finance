package com.datn.financeapp.user.controller;

import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.user.dto.request.AddPermissionRoleRequest;
import com.datn.financeapp.user.dto.response.PermissionResponse;
import com.datn.financeapp.user.dto.response.RoleResponse;
import com.datn.financeapp.user.enums.RoleName;
import com.datn.financeapp.user.service.ManagerRoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/role")
@RequiredArgsConstructor
public class ManagerRoleController {

    private final ManagerRoleService adminRoleService;

    @GetMapping("/all")
    @PreAuthorize("hasAuthority(T(com.datn.financeapp.user.enums.PermissionName).ROLES_READ.getValue())")
    public ApiResponse<List<RoleResponse>> findRoles() {
        return ApiResponse.of(adminRoleService.findRoles());
    }

    @GetMapping("/{roleName}/permissions")
    @PreAuthorize("hasAuthority(T(com.datn.financeapp.user.enums.PermissionName).ROLE_PERMISSION_READ.getValue())")
    public ApiResponse<List<PermissionResponse>> findByRole(@PathVariable RoleName roleName) {
        return ApiResponse.of(adminRoleService.findByRole(roleName));
    }

    @PostMapping("/permission")
    @PreAuthorize("hasAuthority(T(com.datn.financeapp.user.enums.PermissionName).ROLE_PERMISSION_UPDATE.getValue())")
    public ApiResponse<Void> addPermissionToRole(
            @Valid @RequestBody AddPermissionRoleRequest req
    ) {
        adminRoleService.addPermissionToRole(req);
        return ApiResponse.of(null, "Gán quyền hạn cho vai trò thành công");
    }

}

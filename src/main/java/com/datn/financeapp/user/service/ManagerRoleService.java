package com.datn.financeapp.user.service;

import com.datn.financeapp.user.dto.request.AddPermissionRoleRequest;
import com.datn.financeapp.user.dto.response.PermissionResponse;
import com.datn.financeapp.user.dto.response.RoleResponse;
import com.datn.financeapp.user.enums.PermissionName;
import com.datn.financeapp.user.enums.RoleName;

import java.util.List;
import java.util.UUID;

public interface ManagerRoleService {

    void addPermissionToRole(UUID roleId, PermissionName name);

    void addPermissionToRole(AddPermissionRoleRequest req);

    List<RoleResponse> findRoles();

    List<PermissionResponse> findByRole(RoleName roleName);
}

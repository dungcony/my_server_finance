package com.datn.financeapp.module.user.service;

import com.datn.financeapp.module.user.dto.request.AddPermissionRoleRequest;
import com.datn.financeapp.module.user.dto.response.PermissionResponse;
import com.datn.financeapp.module.user.dto.response.RoleResponse;
import com.datn.financeapp.module.user.enums.RoleName;

import java.util.List;

public interface ManagerRoleService {

    void addPermissionToRole(AddPermissionRoleRequest req);

    List<RoleResponse> findRoles();

    List<PermissionResponse> findByRole(RoleName roleName);
}

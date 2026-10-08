package com.datn.financeapp.user.service;

import com.datn.financeapp.user.dto.request.AddPermissionRoleRequest;
import com.datn.financeapp.user.dto.response.PermissionResponse;
import com.datn.financeapp.user.dto.response.RoleResponse;
import com.datn.financeapp.user.enums.RoleName;

import java.util.List;

public interface ManagerRoleService {

    void addPermissionToRole(AddPermissionRoleRequest req);

    List<RoleResponse> findRoles();

    List<PermissionResponse> findByRole(RoleName roleName);
}

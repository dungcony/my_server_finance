package com.datn.financeapp.user.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.user.dto.request.AddPermissionRoleRequest;
import com.datn.financeapp.user.dto.response.PermissionResponse;
import com.datn.financeapp.user.dto.response.RoleResponse;
import com.datn.financeapp.user.entity.Permission;
import com.datn.financeapp.user.entity.Role;
import com.datn.financeapp.user.entity.RolePermission;
import com.datn.financeapp.user.enums.PermissionName;
import com.datn.financeapp.user.enums.RoleName;
import com.datn.financeapp.user.mapper.RoleMapper;
import com.datn.financeapp.user.repository.PermissionRepository;
import com.datn.financeapp.user.repository.RolePermissionRepository;
import com.datn.financeapp.user.repository.RoleRepository;
import com.datn.financeapp.user.service.ManagerRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManagerRoleServiceImpl implements ManagerRoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;

    private final RoleMapper roleMapper;

    @Transactional
    @Override
    public void addPermissionToRole(UUID roleId, PermissionName permissionName) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy vai trò."));
        addPermissionInternal(role, permissionName);
    }

    @Transactional
    @Override
    public void addPermissionToRole(AddPermissionRoleRequest req) {
        Role role = findRole(req.roleName());
        addPermissionInternal(role, req.permissionName());
    }

    @Override
    public List<RoleResponse> findRoles() {
        return roleRepository.findAll()
                .stream()
                .map(roleMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<PermissionResponse> findByRole(RoleName roleName) {
        Role role = findRole(roleName);
        return roleMapper.mapPermissions(role);
    }

    private void addPermissionInternal(Role role, PermissionName permissionName) {
        if (permissionName == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Vui lòng cung cấp quyền hạn cần gán.");
        }

        Permission permission = permissionRepository.findByName(permissionName)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy quyền hạn."));

        if (!rolePermissionRepository.existsByRoleIdAndPermissionId(role.getId(), permission.getId())) {
            RolePermission rolePermission = new RolePermission(role.getId(), permission.getId());
            rolePermission.setRole(role);
            rolePermission.setPermission(permission);
            rolePermissionRepository.save(rolePermission);
            role.getRolePermissions().add(rolePermission);
            log.info("Đã gán permission {} cho role {}", permission.getName().getValue(), role.getName().name());
        }
    }

    private Role findRole(RoleName roleName) {
        if (roleName == null)
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Định danh vai trò không được để trống.");

        return roleRepository.findByName(roleName)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy vai trò."));
    }

}

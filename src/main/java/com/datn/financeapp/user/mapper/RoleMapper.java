package com.datn.financeapp.user.mapper;

import com.datn.financeapp.user.dto.response.PermissionResponse;
import com.datn.financeapp.user.dto.response.RoleResponse;
import com.datn.financeapp.user.entity.Role;
import com.datn.financeapp.user.entity.RolePermission;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.Objects;

@Mapper(componentModel = "spring")
public interface RoleMapper {

    @Mapping(target = "desc", source = "description")
    @Mapping(target = "permissions", expression = "java(mapPermissions(role))")
    RoleResponse toResponse(Role role);

    default List<RoleResponse> toResponseList(List<Role> roles) {
        return roles == null ? List.of() : roles.stream().map(this::toResponse).toList();
    }

    default List<PermissionResponse> mapPermissions(Role role) {
        if (role == null || role.getRolePermissions() == null) {
            return List.of();
        }
        return role.getRolePermissions().stream()
                .map(RolePermission::getPermission)
                .filter(Objects::nonNull)
                .map(p -> new PermissionResponse(p.getName(), p.getDescription()))
                .toList();
    }
}

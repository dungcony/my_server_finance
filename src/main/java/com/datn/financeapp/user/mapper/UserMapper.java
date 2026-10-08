package com.datn.financeapp.user.mapper;

import com.datn.financeapp.user.dto.request.UserCreateReq;
import com.datn.financeapp.user.dto.response.RoleResponse;
import com.datn.financeapp.user.dto.response.UserRes;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.entity.UserRole;
import com.datn.financeapp.user.helper.RolePermissionCacheHelper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Objects;

@Mapper(componentModel = "spring")
public abstract class UserMapper {

    protected RolePermissionCacheHelper rolePermissionCacheHelper;

    @Autowired
    public void setRolePermissionCacheHelper(RolePermissionCacheHelper rolePermissionCacheHelper) {
        this.rolePermissionCacheHelper = rolePermissionCacheHelper;
    }

    @Mapping(target = "password", source = "password")
    @Mapping(target = "isDeleted", expression = "java(user.isDeleted())")
    @Mapping(target = "roles", expression = "java(mapRoles(user))")
    public abstract UserRes toResponse(User user);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "plan", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "userRoles", ignore = true)
    public abstract User toEntity(UserCreateReq req);

    protected List<RoleResponse> mapRoles(User user) {
        if (user == null || user.getUserRoles() == null) {
            return List.of();
        }
        return user.getUserRoles().stream()
                .map(UserRole::getRole)
                .filter(Objects::nonNull)
                .map(role -> {
                    if (rolePermissionCacheHelper != null) {
                        return rolePermissionCacheHelper.findRoleResponseByName(role.getName())
                                .orElseGet(() -> new RoleResponse(role.getName(), role.getLevel(), List.of(), role.getDescription()));
                    }
                    return new RoleResponse(role.getName(), role.getLevel(), List.of(), role.getDescription());
                })
                .toList();
    }
}

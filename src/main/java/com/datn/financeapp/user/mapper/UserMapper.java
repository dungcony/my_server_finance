package com.datn.financeapp.user.mapper;

import com.datn.financeapp.user.dto.response.*;
import com.datn.financeapp.user.entity.RolePermission;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.entity.UserRole;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserProfileResponse toProfile(User user);

    @Mapping(target = "password", source = "password")
    @Mapping(target = "isDeleted", expression = "java(user.isDeleted())")
    @Mapping(target = "roles", expression = "java(mapRoles(user))")
    UserAccountResponse toAccountResponse(User user);

    default List<RoleResponse> mapRoles(User user) {
        if (user == null || user.getUserRoles() == null) {
            return List.of();
        }
        return user.getUserRoles().stream()
                .map(UserRole::getRole)
                .filter(Objects::nonNull)
                .map(role -> {
                    List<PermissionResponse> permissions = role.getRolePermissions() != null
                            ? role.getRolePermissions().stream()
                            .map(RolePermission::getPermission)
                            .filter(Objects::nonNull)
                            .map(p -> new PermissionResponse(p.getName(), p.getDescription()))
                            .toList()
                            : List.of();
                    return new RoleResponse(role.getName(), role.getLevel(), permissions, role.getDescription());
                })
                .toList();
    }

    default List<String> toAuthorityStrings(List<RoleResponse> roles) {
        if (roles == null) {
            return List.of();
        }
        Set<String> authorities = new HashSet<>();
        for (RoleResponse role : roles) {
            authorities.add(role.name().name());
            if (role.permissions() == null) {
                continue;
            }
            for (PermissionResponse permission : role.permissions()) {
                authorities.add(permission.name().getValue());
            }
        }
        return new ArrayList<>(authorities);
    }
}

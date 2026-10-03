package com.datn.financeapp.user.mapper;

import com.datn.financeapp.user.dto.request.UserCreateReq;
import com.datn.financeapp.user.dto.response.*;
import com.datn.financeapp.user.entity.RolePermission;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.entity.UserRole;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.Objects;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "hasPassword", expression = "java(user.getPassword() != null)")
    UserProfileResponse toProfile(User user);

    @Mapping(target = "password", source = "password")
    @Mapping(target = "isDeleted", expression = "java(user.isDeleted())")
    @Mapping(target = "roles", expression = "java(mapRoles(user))")
    UserRes toResponse(User user);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "plan", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "userRoles", ignore = true)
    User toEntity(UserCreateReq req);

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

}

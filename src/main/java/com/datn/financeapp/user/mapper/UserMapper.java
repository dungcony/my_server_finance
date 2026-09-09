package com.datn.financeapp.user.mapper;

import com.datn.financeapp.user.dto.response.UserAccountResponse;
import com.datn.financeapp.user.dto.response.UserDetailResponse;
import com.datn.financeapp.user.dto.response.UserStatsResponse;
import com.datn.financeapp.user.dto.response.UserSummaryResponse;
import com.datn.financeapp.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserSummaryResponse toSummary(User user);

    @Mapping(target = "password", source = "password")
    @Mapping(target = "isDeleted", expression = "java(user.isDeleted())")
    UserAccountResponse toAccountResponse(User user);

    @Mapping(target = "id", source = "user.id")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "firstName", source = "user.firstName")
    @Mapping(target = "lastName", source = "user.lastName")
    @Mapping(target = "avatarUrl", source = "user.avatarUrl")
    @Mapping(target = "plan", source = "user.plan")
    @Mapping(target = "status", source = "user.status")
    @Mapping(target = "password", source = "user.password")
    @Mapping(target = "googleId", source = "user.googleId")
    @Mapping(target = "createdAt", source = "user.createdAt")
    @Mapping(target = "lastLoginAt", source = "user.lastLoginAt")
    @Mapping(target = "stats", source = "stats")
    UserDetailResponse toDetail(User user, UserStatsResponse stats);
}

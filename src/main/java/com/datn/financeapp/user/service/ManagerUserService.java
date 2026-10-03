package com.datn.financeapp.user.service;

import com.datn.financeapp.user.dto.request.BlockUserRequest;
import com.datn.financeapp.user.dto.request.UpdateUserRoleReq;
import com.datn.financeapp.user.dto.response.UserRes;

import java.util.List;
import java.util.UUID;

public interface ManagerUserService {

    void lockUser(UUID managerId, BlockUserRequest req);

    void addRoleToUser(UpdateUserRoleReq req);

    void removeRoleToUser(UpdateUserRoleReq req);

    UserRes deleteByUserId(UUID userId);

    UserRes findByUserId(UUID userId);

    List<UserRes> findAllUser();
}

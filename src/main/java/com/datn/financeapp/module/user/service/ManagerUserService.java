package com.datn.financeapp.module.user.service;

import com.datn.financeapp.module.user.dto.request.BlockUserRequest;
import com.datn.financeapp.module.user.dto.request.UserUnlockRequest;
import com.datn.financeapp.module.user.dto.request.UserRoleUpdateReq;
import com.datn.financeapp.module.user.dto.response.UserRes;

import java.util.List;
import java.util.UUID;

public interface ManagerUserService {

    void lockUser(UUID managerId, BlockUserRequest req);

    void unlockUser(UUID managerId, UserUnlockRequest req);

    void addRoleToUser(UserRoleUpdateReq req);

    void removeRoleToUser(UserRoleUpdateReq req);

    UserRes deleteByUserId(UUID userId);

    UserRes unDeleteByUserId(UUID userId);

    UserRes findByUserId(UUID userId);

    List<UserRes> findAllUser();
}

package com.datn.financeapp.user.service;

import com.datn.financeapp.user.dto.request.BlockUserRequest;
import com.datn.financeapp.user.dto.request.UpdateUserRoleReq;
import com.datn.financeapp.user.dto.response.UserAccountResponse;

import java.util.List;
import java.util.UUID;

public interface ManagerAccountService {

    void blockUser(BlockUserRequest req);

    void addRoleToUser(UpdateUserRoleReq req);

    void removeRoleToUser(UpdateUserRoleReq req);

    UserAccountResponse deleteByUserId(UUID userId);

    UserAccountResponse findByUserId(UUID userId);

    List<UserAccountResponse> findAllUser();
}

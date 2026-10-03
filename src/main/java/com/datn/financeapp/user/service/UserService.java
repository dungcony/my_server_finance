package com.datn.financeapp.user.service;

import com.datn.financeapp.user.dto.request.UserCreateReq;
import com.datn.financeapp.user.dto.request.UserGetReq;
import com.datn.financeapp.user.dto.response.UserRes;
import com.datn.financeapp.user.enums.UserStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface UserService {

    UserRes create(UserCreateReq req);

    UserRes get(UserGetReq req);

    Map<UUID, UserRes> getNames(List<UUID> ids, List<String> email);

    UserRes updateStatus(String email, UserStatus status);

    UserRes updatePass(String email, String password);

    // Đăng nhập Google: đã liên kết googleId thì trả về luôn, trùng email thì liên kết vào tài khoản đó,
    // chưa có gì thì tạo mới. Ném UserNotFoundException/UserBlockedException nếu tài khoản không đủ điều kiện.
    UserRes resolveGoogleUser(String email, String googleId, Instant now);

    boolean existByEmail(String email);

    void validUser(String email);


}

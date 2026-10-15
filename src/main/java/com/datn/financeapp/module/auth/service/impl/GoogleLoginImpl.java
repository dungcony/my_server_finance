package com.datn.financeapp.module.auth.service.impl;

import com.datn.financeapp.module.auth.dto.request.GoogleLoginRequest;
import com.datn.financeapp.module.auth.dto.request.TokenCreateReq;
import com.datn.financeapp.module.auth.dto.response.AuthRes;
import com.datn.financeapp.module.auth.helper.ClientInfo;
import com.datn.financeapp.module.auth.helper.GoogleUserInfo;
import com.datn.financeapp.module.auth.service.GoogleService;
import com.datn.financeapp.module.auth.service.LoginService;
import com.datn.financeapp.module.auth.service.TokenService;
import com.datn.financeapp.module.user.dto.response.UserRes;
import com.datn.financeapp.module.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleLoginImpl implements LoginService<GoogleLoginRequest> {

    private final GoogleService googleService;
    private final UserService userService;
    private final TokenService tokenService;

    @Override
    public AuthRes login(GoogleLoginRequest req, ClientInfo client) {
        // Xác thực idToken
        GoogleUserInfo googleUserInfo = googleService.verifyIdToken(req.idToken());
        // Resolve user từ googleId/email
        UserRes user = userService.resolveGoogleUser(googleUserInfo.email(), googleUserInfo.googleId());
        // Tạo token và trả về response
        return new AuthRes(
                user,
                tokenService.create(new TokenCreateReq(
                        user.id(),
                        user.email(),
                        user.plan().name(),
                        user.getAuthorities(),
                        user.getTopRoleLevel()
                ))
        );
    }
}
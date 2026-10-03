package com.datn.financeapp.auth.controller;

import com.datn.financeapp.auth.dto.request.EmailLoginRequest;
import com.datn.financeapp.auth.dto.request.GoogleLoginRequest;
import com.datn.financeapp.auth.helper.ClientInfo;
import com.datn.financeapp.auth.service.LoginService;
import com.datn.financeapp.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller chuyên trách xác thực và vòng đời phiên đăng nhập.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class LoginController {
    private final LoginService<EmailLoginRequest> emailLogin;
    private final LoginService<GoogleLoginRequest> googleLogin;

    @PostMapping("/login")
    public ApiResponse<?> login(
            @Valid @RequestBody EmailLoginRequest req,
            HttpServletRequest httpReq) {
        return ApiResponse.of(emailLogin.login(req, ClientInfo.from(httpReq)));
    }

    @PostMapping("/google")
    public ApiResponse<?> loginWithGoogle(
            @Valid @RequestBody GoogleLoginRequest req,
            HttpServletRequest httpReq) {
        return ApiResponse.of(googleLogin.login(req, ClientInfo.from(httpReq)));
    }
}

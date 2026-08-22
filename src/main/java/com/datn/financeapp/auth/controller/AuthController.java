package com.datn.financeapp.auth.controller;

import com.datn.financeapp.auth.dto.AuthResponse;
import com.datn.financeapp.auth.dto.LoginRequest;
import com.datn.financeapp.auth.dto.LogoutRequest;
import com.datn.financeapp.auth.dto.RefreshRequest;
import com.datn.financeapp.auth.dto.RefreshResponse;
import com.datn.financeapp.auth.dto.RegisterRequest;
import com.datn.financeapp.auth.service.AuthService;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.security.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 4 endpoint lõi vòng đời phiên đăng nhập: register/login/refresh/logout.
 *
 * D-11: KHÔNG gắn {@code @Idempotent} lên bất kỳ method nào ở đây — đây là POST-hành-động
 * (login/refresh/logout) hoặc trường hợp đặc biệt (register), D-11 đã chốt loại trừ toàn bộ
 * {@code /auth/**} dù về lý thuyết register "tạo mới tài nguyên" có vẻ hợp lý để gắn.
 *
 * {@code /auth/register}, {@code /auth/login}, {@code /auth/refresh} nằm trong permitAll của
 * SecurityConfig (Plan 02); {@code /auth/logout} yêu cầu Bearer token hợp lệ (T-04-06 — chỉ
 * chủ token mới gọi được logout_all_devices).
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ApiResponse.of(authService.register(req));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest httpReq) {
        String ip = ClientIpResolver.resolve(httpReq);
        String userAgent = httpReq.getHeader("User-Agent");
        return ApiResponse.of(authService.login(req, ip, userAgent));
    }

    @PostMapping("/refresh")
    public ApiResponse<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return ApiResponse.of(authService.refresh(req));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody LogoutRequest req) {
        authService.logout(req.refreshToken(), req.logoutAllDevices());
        return ApiResponse.of(null);
    }
}

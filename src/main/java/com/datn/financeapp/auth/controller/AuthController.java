package com.datn.financeapp.auth.controller;

import com.datn.financeapp.auth.dto.AuthResponse;
import com.datn.financeapp.auth.dto.ChangePasswordRequest;
import com.datn.financeapp.auth.dto.DeleteAccountRequest;
import com.datn.financeapp.auth.dto.ForgotPasswordRequest;
import com.datn.financeapp.auth.dto.GoogleLoginRequest;
import com.datn.financeapp.auth.dto.LoginRequest;
import com.datn.financeapp.auth.dto.LogoutRequest;
import com.datn.financeapp.auth.dto.RefreshRequest;
import com.datn.financeapp.auth.dto.RefreshResponse;
import com.datn.financeapp.auth.dto.RegisterRequest;
import com.datn.financeapp.auth.dto.ResetPasswordRequest;
import com.datn.financeapp.auth.dto.UpdateProfileRequest;
import com.datn.financeapp.auth.dto.UserDetailDto;
import com.datn.financeapp.auth.dto.UserSummaryDto;
import com.datn.financeapp.auth.service.AuthService;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.security.ClientIpResolver;
import com.datn.financeapp.common.security.SecurityContextUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

    /**
     * D3: trả 200 cho cả trường hợp tạo tài khoản mới, không phải 201 như {@code /register}.
     * Người dùng bấm một nút và mong vào thẳng — họ không phân biệt "đăng ký" với "đăng nhập",
     * nên API cũng không nên bắt app xử lý hai nhánh (api/01 mục 13).
     */
    @PostMapping("/google")
    public ApiResponse<AuthResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest req) {
        return ApiResponse.of(authService.loginWithGoogle(req));
    }

    @PostMapping("/refresh")
    public ApiResponse<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return ApiResponse.of(authService.refresh(req));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody LogoutRequest req) {
        authService.logout(SecurityContextUtil.currentUserId(), req.refreshToken(), req.logoutAllDevices());
        return ApiResponse.of(null);
    }

    @GetMapping("/me")
    public ApiResponse<UserDetailDto> me() {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(authService.getMe(userId));
    }

    @PatchMapping("/me")
    public ApiResponse<UserSummaryDto> updateProfile(@Valid @RequestBody UpdateProfileRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(authService.updateProfile(userId, req));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        authService.changePassword(userId, req);
        return ApiResponse.of(null);
    }

    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        // AuthService.forgotPassword không throw ở bất kỳ nhánh nào — response LUÔN 200 giống
        // hệt nhau dù email tồn tại hay không (T-05-01, chống dò danh sách người dùng).
        authService.forgotPassword(req);
        return ApiResponse.of(null);
    }

    /**
     * C2 — xoá tài khoản (api/01-XAC-THUC.md mục 10). DELETE có body: đặc tả bắt nhập lại mật
     * khẩu, và mật khẩu không được phép nằm trên query string (lộ trong log máy chủ, lịch sử
     * trình duyệt). Endpoint không nằm trong permitAll của SecurityConfig nên đã bắt buộc
     * Bearer token; danh tính lấy từ JWT, client không tự chỉ định được xoá tài khoản nào.
     */
    @DeleteMapping("/account")
    public ApiResponse<Void> deleteAccount(@Valid @RequestBody DeleteAccountRequest req) {
        authService.deleteAccount(SecurityContextUtil.currentUserId(), req);
        return ApiResponse.of(null);
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req);
        return ApiResponse.of(null);
    }
}

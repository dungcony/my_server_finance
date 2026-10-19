package com.datn.financeapp.unitTest.auth.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.mail.EmailService;
import com.datn.financeapp.module.auth.dto.request.RefreshRequest;
import com.datn.financeapp.module.auth.dto.request.RegisterRequest;
import com.datn.financeapp.module.auth.dto.request.ResendVerificationRequest;
import com.datn.financeapp.module.auth.dto.response.RegisterRes;
import com.datn.financeapp.module.auth.entity.OtpModel;
import com.datn.financeapp.module.auth.enums.OtpType;
import com.datn.financeapp.module.auth.repository.OtpRepository;
import com.datn.financeapp.module.auth.service.TokenService;
import com.datn.financeapp.module.auth.service.impl.AuthServiceImpl;
import com.datn.financeapp.module.user.dto.request.UserCreateReq;
import com.datn.financeapp.module.user.dto.request.UserGetReq;
import com.datn.financeapp.module.user.dto.response.UserRes;
import com.datn.financeapp.module.user.enums.UserPlan;
import com.datn.financeapp.module.user.enums.UserStatus;
import com.datn.financeapp.module.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserService userService;

    @Mock
    private TokenService tokenService;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordEncoder passwordEncoder;


    @Mock
    private OtpRepository otpRepository;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    @DisplayName("register: Email da ton tai -> nem AUTH_EMAIL_ALREADY_EXISTS")
    void register_EmailExists_ThrowsException() {
        RegisterRequest req = new RegisterRequest("User@Example.COM", "password123");

        when(userService.existByEmail("user@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.AUTH_EMAIL_ALREADY_EXISTS.getCode());

        verify(userService, never()).create(any());
    }

    @Test
    @DisplayName("register: Dang ky hop le -> chuan hoa lowercase email va gui OTP")
    void register_Valid_Success() {
        RegisterRequest req = new RegisterRequest("User@Example.COM", "password123");
        UUID userId = UUID.randomUUID();
        UserRes createdUser = new UserRes(userId, "user@example.com", "User", "Name", "", UserPlan.FREE, UserStatus.PENDING_VERIFY, List.of(), "encodedPassword", null, false);

        when(userService.existByEmail("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
        when(userService.create(any(UserCreateReq.class))).thenReturn(createdUser);

        RegisterRes res = authService.register(req);

        assertThat(res).isNotNull();
        assertThat(res.email()).isEqualTo("user@example.com");
        assertThat(res.id()).isEqualTo(userId);
        verify(emailService).sendVerificationOtp(eq("user@example.com"), anyString());
    }

    @Test
    @DisplayName("resendVerification: Tai khoan bi khoa (BLOCKED) -> nem AUTH_ACCOUNT_BLOCKED")
    void resendVerification_AccountBlocked_ThrowsException() {
        ResendVerificationRequest req = new ResendVerificationRequest("blocked@example.com");
        UserRes blockedUser = new UserRes(UUID.randomUUID(), "blocked@example.com", "User", null, null, UserPlan.FREE, UserStatus.BLOCKED, List.of(), null, null, false);

        when(userService.get(any(UserGetReq.class))).thenReturn(blockedUser);

        assertThatThrownBy(() -> authService.resendVerification(req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.AUTH_ACCOUNT_BLOCKED.getCode());
    }

    @Test
    @DisplayName("resendVerification: Tai khoan da kich hoat (ACTIVE) -> nem AUTH_ACCOUNT_ALREADY_VERIFIED")
    void resendVerification_AlreadyVerified_ThrowsException() {
        ResendVerificationRequest req = new ResendVerificationRequest("active@example.com");
        UserRes activeUser = new UserRes(UUID.randomUUID(), "active@example.com", "User", null, null, UserPlan.FREE, UserStatus.ACTIVE, List.of(), null, null, false);

        when(userService.get(any(UserGetReq.class))).thenReturn(activeUser);

        assertThatThrownBy(() -> authService.resendVerification(req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.AUTH_ACCOUNT_ALREADY_VERIFIED.getCode());
    }

    @Test
    @DisplayName("resendVerification: Yeu cau gui lai trong vong 60s -> nem RATE_LIMIT_EXCEEDED")
    void resendVerification_RateLimited_ThrowsException() {
        ResendVerificationRequest req = new ResendVerificationRequest("user@example.com");
        UserRes pendingUser = new UserRes(UUID.randomUUID(), "user@example.com", "User", null, null, UserPlan.FREE, UserStatus.PENDING_VERIFY, List.of(), null, null, false);

        OtpModel recentOtp = OtpModel.builder()
                .email("user@example.com")
                .type(OtpType.REGISTER_OTP)
                .code("123456")
                .createdAt(Instant.now().minus(20, ChronoUnit.SECONDS))
                .ttl(15L)
                .build();

        when(userService.get(any(UserGetReq.class))).thenReturn(pendingUser);
        when(otpRepository.findByTypeAndEmail(OtpType.REGISTER_OTP, "user@example.com")).thenReturn(Optional.of(recentOtp));

        assertThatThrownBy(() -> authService.resendVerification(req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.RATE_LIMIT_EXCEEDED.getCode());
    }

    @Test
    @DisplayName("refresh: Tai khoan bi khoa khi doi token -> nem AUTH_ACCOUNT_BLOCKED")
    void refresh_AccountBlocked_ThrowsException() {
        RefreshRequest req = new RefreshRequest("valid-refresh-token");
        UUID userId = UUID.randomUUID();
        UserRes blockedUser = new UserRes(userId, "user@example.com", "User", null, null, UserPlan.FREE, UserStatus.BLOCKED, List.of(), null, null, false);

        when(tokenService.checkRefreshAndGetUserId("valid-refresh-token")).thenReturn(userId);
        when(userService.get(any(UserGetReq.class))).thenReturn(blockedUser);

        assertThatThrownBy(() -> authService.refresh(req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.AUTH_ACCOUNT_BLOCKED.getCode());
    }

    @Test
    @DisplayName("logout: isAll = true thi thu hoi toan bo session cua user")
    void logout_All_RevokesAllSessions() {
        UUID userId = UUID.randomUUID();

        authService.logout(userId, "some-token", true);

        verify(tokenService).revokeAllByUserId(userId);
        verify(tokenService, never()).revokeRefresh(anyString());
    }

    @Test
    @DisplayName("logout: isAll = false thi chi thu hoi dung refreshToken do")
    void logout_Single_RevokesSpecificToken() {
        UUID userId = UUID.randomUUID();

        authService.logout(userId, "specific-token", false);

        verify(tokenService).revokeRefresh("specific-token");
        verify(tokenService, never()).revokeAllByUserId(any());
    }
}

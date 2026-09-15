package com.datn.financeapp.auth.service;

import com.datn.financeapp.auth.dto.request.ForgotPasswordRequest;
import com.datn.financeapp.auth.dto.request.ResetPasswordRequest;
import com.datn.financeapp.auth.entity.OtpModel;
import com.datn.financeapp.auth.enums.OtpType;
import com.datn.financeapp.auth.exception.AuthPasswordSameAsOldException;
import com.datn.financeapp.auth.exception.AuthResetCodeInvalidException;
import com.datn.financeapp.auth.repository.LoginAttemptRepository;
import com.datn.financeapp.auth.repository.OtpRepository;
import com.datn.financeapp.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.auth.service.ForgotPasswordRateLimiter;
import com.datn.financeapp.auth.service.GoogleService;
import com.datn.financeapp.auth.service.impl.AuthServiceImpl;
import com.datn.financeapp.common.mail.EmailService;
import com.datn.financeapp.common.security.JwtService;
import com.datn.financeapp.user.dto.response.UserAccountResponse;
import com.datn.financeapp.user.enums.UserPlan;
import com.datn.financeapp.user.enums.UserStatus;
import com.datn.financeapp.user.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthResetPasswordServiceTest {

    @Mock
    private AccountService userAccountService;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private LoginAttemptRepository loginAttemptRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private EmailService emailService;
    @Mock
    private ForgotPasswordRateLimiter forgotPasswordRateLimiter;
    @Mock
    private GoogleService googleIdTokenVerifier;
    @Mock
    private OtpRepository otpRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AuthServiceImpl authService;

    private final UUID userId = UUID.randomUUID();
    private final String email = "test@example.com";

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                userAccountService,
                refreshTokenRepository,
                loginAttemptRepository,
                passwordEncoder,
                jwtService,
                emailService,
                forgotPasswordRateLimiter,
                googleIdTokenVerifier,
                otpRepository,
                eventPublisher
        );
    }

    @Test
    @DisplayName("forgotPassword: Email tồn tại -> Lưu OtpModel vào Redis và gửi mail")
    void forgotPassword_UserExists_SavesOtpToRedisAndSendsMail() {
        when(userAccountService.findUserIdForPasswordReset(email)).thenReturn(Optional.of(userId));

        authService.forgotPassword(new ForgotPasswordRequest(email));

        ArgumentCaptor<OtpModel> otpCaptor = ArgumentCaptor.forClass(OtpModel.class);
        verify(otpRepository).save(otpCaptor.capture());

        OtpModel savedOtp = otpCaptor.getValue();
        assertThat(savedOtp.getEmail()).isEqualTo(email);
        assertThat(savedOtp.getType()).isEqualTo(OtpType.PASSWORD_RESET_OTP);
        assertThat(savedOtp.getCode()).matches("\\d{6}");
        assertThat(savedOtp.getTtl()).isEqualTo(15L);

        verify(emailService).sendPasswordResetCode(eq(email), eq(savedOtp.getCode()));
    }

    @Test
    @DisplayName("forgotPassword: Email không tồn tại -> Không lưu Redis, không gửi mail, không ném exception")
    void forgotPassword_UserNotFound_DoesNothing() {
        when(userAccountService.findUserIdForPasswordReset(email)).thenReturn(Optional.empty());

        authService.forgotPassword(new ForgotPasswordRequest(email));

        verify(otpRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetCode(any(), any());
    }
    @Test
    @DisplayName("resetPassword: Mật khẩu mới trùng mật khẩu cũ -> Ném AuthPasswordSameAsOldException")
    void resetPassword_NewPasswordMatchesOldPassword_ThrowsAuthPasswordSameAsOldException() {
        String code = "123456";
        String samePassword = "currentPassword123";

        OtpModel otp = OtpModel.builder()
                .email(email)
                .type(OtpType.PASSWORD_RESET_OTP)
                .code(code)
                .ttl(15L)
                .createdAt(Instant.now())
                .build();

        when(otpRepository.findByTypeAndEmail(OtpType.PASSWORD_RESET_OTP, email)).thenReturn(Optional.of(otp));

        UserAccountResponse user = new UserAccountResponse(
                userId, email, "encodedCurrentPassword", UserPlan.FREE, UserStatus.ACTIVE,
                Collections.emptyList(), false, null
        );
        when(userAccountService.findByEmail(email)).thenReturn(user);
        when(passwordEncoder.matches(samePassword, "encodedCurrentPassword")).thenReturn(true);

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest(email, code, samePassword)))
                .isInstanceOf(AuthPasswordSameAsOldException.class)
                .hasFieldOrPropertyWithValue("code", "NEW_PASSWORD_SAME_AS_OLD");

        verify(userAccountService, never()).resetPasswordWithCode(any(), any());
        verify(otpRepository, never()).deleteByTypeAndEmail(any(), any());
        verify(refreshTokenRepository, never()).revokeAllActiveForUser(any());
    }

    @Test
    @DisplayName("resetPassword: Mã OTP khớp -> Đổi mật khẩu, xóa OTP trong Redis, thu hồi refresh tokens")
    void resetPassword_ValidOtp_ResetsPasswordAndDeletesOtp() {
        String code = "123456";
        String newPassword = "123456";

        OtpModel otp = OtpModel.builder()
                .email(email)
                .type(OtpType.PASSWORD_RESET_OTP)
                .code(code)
                .ttl(15L)
                .createdAt(Instant.now())
                .build();

        when(otpRepository.findByTypeAndEmail(OtpType.PASSWORD_RESET_OTP, email)).thenReturn(Optional.of(otp));

        UserAccountResponse user = new UserAccountResponse(
                userId, email, "1234567", UserPlan.FREE, UserStatus.ACTIVE,
                Collections.emptyList(), false, null
        );
        when(userAccountService.findByEmail(email)).thenReturn(user);

        authService.resetPassword(new ResetPasswordRequest(email, code, newPassword));

        verify(userAccountService).resetPasswordWithCode(userId, newPassword);
        verify(otpRepository).deleteByTypeAndEmail(OtpType.PASSWORD_RESET_OTP, email);
        verify(refreshTokenRepository).revokeAllActiveForUser(userId);
    }

    @Test
    @DisplayName("resetPassword: Mã OTP sai -> Ném AuthResetCodeInvalidException")
    void resetPassword_WrongCode_ThrowsException() {
        OtpModel otp = OtpModel.builder()
                .email(email)
                .type(OtpType.PASSWORD_RESET_OTP)
                .code("123456")
                .ttl(15L)
                .createdAt(Instant.now())
                .build();

        when(otpRepository.findByTypeAndEmail(OtpType.PASSWORD_RESET_OTP, email)).thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest(email, "999999", "newPassword123")))
                .isInstanceOf(AuthResetCodeInvalidException.class);

        verify(userAccountService, never()).resetPasswordWithCode(any(), any());
        verify(otpRepository, never()).deleteByTypeAndEmail(any(), any());
    }

    @Test
    @DisplayName("resetPassword: Không tìm thấy OTP trong Redis (hết hạn) -> Ném AuthResetCodeInvalidException")
    void resetPassword_OtpNotFound_ThrowsException() {
        when(otpRepository.findByTypeAndEmail(OtpType.PASSWORD_RESET_OTP, email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest(email, "123456", "newPassword123")))
                .isInstanceOf(AuthResetCodeInvalidException.class);

        verify(userAccountService, never()).resetPasswordWithCode(any(), any());
    }

    @Test
    @DisplayName("resetPassword: User bị khóa -> Ném AuthResetCodeInvalidException")
    void resetPassword_UserBlocked_ThrowsException() {
        OtpModel otp = OtpModel.builder()
                .email(email)
                .type(OtpType.PASSWORD_RESET_OTP)
                .code("123456")
                .ttl(15L)
                .createdAt(Instant.now())
                .build();

        when(otpRepository.findByTypeAndEmail(OtpType.PASSWORD_RESET_OTP, email)).thenReturn(Optional.of(otp));

        UserAccountResponse user = new UserAccountResponse(
                userId, email, "oldHash", UserPlan.FREE, UserStatus.BLOCKED,
                Collections.emptyList(), false, null
        );
        when(userAccountService.findByEmail(email)).thenReturn(user);

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest(email, "123456", "newPassword123")))
                .isInstanceOf(AuthResetCodeInvalidException.class);

        verify(userAccountService, never()).resetPasswordWithCode(any(), any());
        verify(otpRepository, never()).deleteByTypeAndEmail(any(), any());
    }
}

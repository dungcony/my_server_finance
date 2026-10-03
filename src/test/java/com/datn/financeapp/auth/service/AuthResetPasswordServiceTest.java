package com.datn.financeapp.auth.service;

import com.datn.financeapp.auth.dto.request.ForgotPasswordRequest;
import com.datn.financeapp.auth.dto.request.ResetPasswordRequest;
import com.datn.financeapp.auth.entity.OtpModel;
import com.datn.financeapp.auth.enums.OtpType;
import com.datn.financeapp.auth.repository.OtpRepository;
import com.datn.financeapp.auth.service.impl.AuthServiceImpl;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.mail.EmailService;
import com.datn.financeapp.user.service.UserService;
import com.datn.financeapp.auth.helper.ForgotPasswordRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
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
    private UserService userService;
    @Mock
    private TokenService tokenService;
    @Mock
    private EmailService emailService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ForgotPasswordRateLimiter forgotPasswordRateLimiter;
    @Mock
    private OtpRepository otpRepository;

    private AuthServiceImpl authService;

    private final UUID userId = UUID.randomUUID();
    private final String email = "test@example.com";

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                userService,
                tokenService,
                emailService,
                passwordEncoder,
                forgotPasswordRateLimiter,
                otpRepository
        );
    }

    @Test
    @DisplayName("forgotPassword: User hợp lệ -> Lưu OtpModel vào Redis và gửi mail")
    void forgotPassword_UserExists_SavesOtpToRedisAndSendsMail() {
        doNothing().when(userService).validUser(email);

        authService.forgotPassword(new ForgotPasswordRequest(email));

        ArgumentCaptor<OtpModel> otpCaptor = ArgumentCaptor.forClass(OtpModel.class);
        verify(otpRepository).save(otpCaptor.capture());

        OtpModel savedOtp = otpCaptor.getValue();
        assertThat(savedOtp.getEmail()).isEqualTo(email);
        assertThat(savedOtp.getType()).isEqualTo(OtpType.PASSWORD_RESET_OTP);
        assertThat(savedOtp.getCode()).matches("\\d{6}");
        assertThat(savedOtp.getTtl()).isEqualTo(15L);

        verify(emailService).sendVerificationOtp(eq(email), eq(savedOtp.getCode()));
    }

    @Test
    @DisplayName("forgotPassword: User không hợp lệ -> Ném exception từ userService.validUser")
    void forgotPassword_UserNotFound_DoesNothing() {
        doThrow(new BusinessException(ErrorCode.NOT_FOUND)).when(userService).validUser(email);

        assertThatThrownBy(() -> authService.forgotPassword(new ForgotPasswordRequest(email)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "NOT_FOUND");

        verify(otpRepository, never()).save(any());
        verify(emailService, never()).sendVerificationOtp(any(), any());
    }

    @Test
    @DisplayName("resetPassword: Mật khẩu mới trùng mật khẩu cũ -> Ném BusinessException(AUTH_PASSWORD_SAME_AS_OLD)")
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
        
        doThrow(new BusinessException(ErrorCode.AUTH_PASSWORD_SAME_AS_OLD))
                .when(userService).updatePass(email, samePassword);

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest(email, code, samePassword)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "AUTH_PASSWORD_SAME_AS_OLD");

        verify(otpRepository, never()).deleteByTypeAndEmail(any(), any());
        verify(tokenService, never()).revokeAllByEmail(any());
    }

    @Test
    @DisplayName("resetPassword: Mã OTP khớp -> Đổi mật khẩu, xóa OTP trong Redis, thu hồi refresh tokens")
    void resetPassword_ValidOtp_ResetsPasswordAndDeletesOtp() {
        String code = "123456";
        String newPassword = "newPassword123";

        OtpModel otp = OtpModel.builder()
                .email(email)
                .type(OtpType.PASSWORD_RESET_OTP)
                .code(code)
                .ttl(15L)
                .createdAt(Instant.now())
                .build();

        when(otpRepository.findByTypeAndEmail(OtpType.PASSWORD_RESET_OTP, email)).thenReturn(Optional.of(otp));

        authService.resetPassword(new ResetPasswordRequest(email, code, newPassword));

        verify(userService).updatePass(email, newPassword);
        verify(otpRepository).deleteByTypeAndEmail(OtpType.PASSWORD_RESET_OTP, email);
        verify(tokenService).revokeAllByEmail(email);
    }

    @Test
    @DisplayName("resetPassword: Mã OTP sai -> Ném BusinessException(AUTH_RESET_CODE_INVALID)")
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
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "AUTH_RESET_CODE_INVALID");

        verify(userService, never()).updatePass(any(), any());
        verify(otpRepository, never()).deleteByTypeAndEmail(any(), any());
    }

    @Test
    @DisplayName("resetPassword: Không tìm thấy OTP trong Redis (hết hạn) -> Ném BusinessException(CODE_INVALID)")
    void resetPassword_OtpNotFound_ThrowsException() {
        when(otpRepository.findByTypeAndEmail(OtpType.PASSWORD_RESET_OTP, email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest(email, "123456", "newPassword123")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "AUTH_RESET_CODE_INVALID");

        verify(userService, never()).updatePass(any(), any());
    }

}


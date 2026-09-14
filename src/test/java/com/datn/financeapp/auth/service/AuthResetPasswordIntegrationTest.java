package com.datn.financeapp.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.auth.dto.request.ForgotPasswordRequest;
import com.datn.financeapp.auth.dto.request.RegisterRequest;
import com.datn.financeapp.auth.dto.request.ResetPasswordRequest;
import com.datn.financeapp.auth.dto.request.VerifyEmailRequest;
import com.datn.financeapp.auth.entity.OtpModel;
import com.datn.financeapp.auth.enums.OtpType;
import com.datn.financeapp.auth.repository.OtpRepository;
import com.datn.financeapp.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.auth.service.AuthService;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.mail.EmailService;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.repository.UserRepository;
import com.datn.financeapp.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Test tích hợp cho chức năng Quên mật khẩu và Đặt lại mật khẩu (Auth Domain: /auth/forgot-password, /auth/reset-password).
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(TestRedisConfig.class)
class AuthResetPasswordIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1hdXRoLXByb2ZpbGUtdGVzdC0zMmI=");
    }

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private OtpRepository otpRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoSpyBean
    private EmailService emailService;

    @BeforeEach
    void cleanTables() {
        otpRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User registerUser(String email, String password, String username) {
        authService.register(new RegisterRequest(email, password, username));
        String code = otpRepository
                .findByTypeAndEmail(OtpType.REGISTER_OTP, email)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy mã OTP đăng ký cho " + email))
                .getCode();
        authService.verifyEmail(new VerifyEmailRequest(email, code));
        return userRepository.findByEmail(email).orElseThrow();
    }

    @Test
    void forgotPassword_existingAndNonExistingEmail_neitherThrows_onlyExistingEmailCreatesToken() {
        registerUser("ton.tai@example.com", "matkhaudung1", "Email Tồn Tại");

        authService.forgotPassword(new ForgotPasswordRequest("ton.tai@example.com"));
        authService.forgotPassword(new ForgotPasswordRequest("khong.ton.tai@example.com"));

        assertThat(otpRepository.findByTypeAndEmail(OtpType.PASSWORD_RESET_OTP, "ton.tai@example.com")).isPresent();
        assertThat(otpRepository.findByTypeAndEmail(OtpType.PASSWORD_RESET_OTP, "khong.ton.tai@example.com")).isEmpty();
    }

    @Test
    void forgotPassword_sendsSixDigitCodeToNotifier() {
        registerUser("sau.chu.so@example.com", "matkhaudung1", "Sáu Chữ Số");

        authService.forgotPassword(new ForgotPasswordRequest("sau.chu.so@example.com"));

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        Mockito.verify(emailService)
                .sendPasswordResetCode(Mockito.eq("sau.chu.so@example.com"), code.capture());
        assertThat(code.getValue()).matches("\\d{6}");
    }

    @Test
    void resetPassword_validUnexpiredCode_succeeds_reuseOnSecondCallIsRejected() {
        User user = registerUser("dat.lai@example.com", "matkhaucu123", "Đặt Lại Mật Khẩu");
        authService.forgotPassword(new ForgotPasswordRequest("dat.lai@example.com"));

        OtpModel otp = otpRepository.findByTypeAndEmail(OtpType.PASSWORD_RESET_OTP, "dat.lai@example.com").orElseThrow();
        String rawResetCode = otp.getCode();

        authService.resetPassword(new ResetPasswordRequest("dat.lai@example.com", rawResetCode, "matkhaumoi789"));

        User reload = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("matkhaumoi789", reload.getPassword())).isTrue();

        assertThat(otpRepository.findByTypeAndEmail(OtpType.PASSWORD_RESET_OTP, "dat.lai@example.com")).isEmpty();

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest("dat.lai@example.com", rawResetCode, "khac123456")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("RESET_CODE_INVALID"));
    }

    @Test
    void resetPassword_expiredCode_returnsResetCodeInvalid() {
        registerUser("het.han@example.com", "matkhaucu123", "Hết Hạn");
        authService.forgotPassword(new ForgotPasswordRequest("het.han@example.com"));

        otpRepository.deleteByTypeAndEmail(OtpType.PASSWORD_RESET_OTP, "het.han@example.com");

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest("het.han@example.com", "123456", "matkhaumoi789")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("RESET_CODE_INVALID"));
    }

    @Test
    void resetPassword_sameAsOldPassword_returnsNewPasswordSameAsOld() {
        User user = registerUser("trung.mk@example.com", "matkhaucu123", "Trùng Mật Khẩu");
        authService.forgotPassword(new ForgotPasswordRequest("trung.mk@example.com"));

        OtpModel otp = otpRepository.findByTypeAndEmail(OtpType.PASSWORD_RESET_OTP, "trung.mk@example.com").orElseThrow();
        String rawResetCode = otp.getCode();

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest("trung.mk@example.com", rawResetCode, "matkhaucu123")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("NEW_PASSWORD_SAME_AS_OLD"));
    }
}

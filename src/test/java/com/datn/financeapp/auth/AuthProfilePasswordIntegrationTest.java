package com.datn.financeapp.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.user.dto.request.UpdatePassReq;
import com.datn.financeapp.auth.dto.request.ForgotPasswordRequest;
import com.datn.financeapp.auth.dto.request.RegisterRequest;
import com.datn.financeapp.auth.dto.request.ResetPasswordRequest;
import com.datn.financeapp.auth.dto.request.VerifyEmailRequest;
import com.datn.financeapp.user.dto.request.UpdateMeRequest;
import com.datn.financeapp.user.dto.response.UserProfileResponse;
import com.datn.financeapp.auth.entity.OtpModel;
import com.datn.financeapp.auth.enums.OtpType;
import com.datn.financeapp.auth.repository.OtpRepository;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.user.repository.UserRepository;
import com.datn.financeapp.auth.service.AuthService;
import com.datn.financeapp.common.mail.EmailService;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.user.service.AccountService;
import com.datn.financeapp.user.service.ProfileService;
import com.datn.financeapp.wallet.repository.WalletRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Test tích hợp gọi trực tiếp {@link AuthService} (Testcontainers PostgreSQL thật) cho AUTH-05
 * (profile) và AUTH-06 (đổi/quên/đặt lại mật khẩu). Nhất quán cách tiếp cận Plan 04 Task 1 — test
 * Service layer, Controller wiring verify riêng ở Task 2 bằng test HTTP thật.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(TestRedisConfig.class)
class AuthProfilePasswordIntegrationTest {

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
    private ProfileService userProfileService;

    @Autowired
    private AccountService userAccountService;

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

    /**
     * Đăng ký rồi xác thực email — {@code register} chỉ tạo tài khoản {@code PENDING_VERIFY} từ
     * 13/09/2026, nên phải qua bước xác thực mới dùng được như tài khoản bình thường.
     */
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
    void patchMe_UpdatesUsername_Succeeds() {
        User user = registerUser("cap.nhat@example.com", "matkhaudung1", "Tên Cũ");

        var result = userProfileService.updateMe(user.getId(), new UpdateMeRequest("Minh", "Nguyễn", null));

        assertThat(result.firstName()).isEqualTo("Minh");
        assertThat(result.lastName()).isEqualTo("Nguyễn");
        User reload = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reload.getFirstName()).isEqualTo("Minh");
        assertThat(reload.getLastName()).isEqualTo("Nguyễn");
        // UpdateProfileRequest không có field email/plan -> record chỉ 2 tham số, không có cách
        // nào gọi truyền email/plan qua đây (verify bằng compile-time, xem field list ở class).
        assertThat(reload.getEmail()).isEqualTo("cap.nhat@example.com");
        assertThat(reload.getPlan()).isEqualTo(com.datn.financeapp.user.enums.UserPlan.FREE);
    }

    @Test
    void getMe_existingUser_returnsProfile() {
        User user = registerUser("xem.ho.so@example.com", "matkhaudung1", "Xem Hồ Sơ");

        UserProfileResponse detail = userProfileService.getMe(user.getId());

        assertThat(detail.id()).isEqualTo(user.getId());
        assertThat(detail.email()).isEqualTo("xem.ho.so@example.com");
        assertThat(detail.plan()).isEqualTo(com.datn.financeapp.user.enums.UserPlan.FREE);
    }

    @Test
    void changePassword_correctOldPassword_revokesAllActiveRefreshTokens() {
        User user = registerUser("doi.matkhau@example.com", "matkhaucu123", "Đổi Mật Khẩu");

        // Giả lập 2 phiên đăng nhập trước đó — mỗi login cấp thêm 1 refresh token active.
        authService.login(
                new com.datn.financeapp.auth.dto.request.LoginRequest("doi.matkhau@example.com", "matkhaucu123"),
                "127.0.0.1", "junit");
        authService.login(
                new com.datn.financeapp.auth.dto.request.LoginRequest("doi.matkhau@example.com", "matkhaucu123"),
                "127.0.0.1", "junit");

        assertThat(refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(user.getId())).isNotEmpty();

        userAccountService.changePassword(user.getId(), new UpdatePassReq("matkhaucu123", "matkhaumoi456"));

        assertThat(refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(user.getId())).isEmpty();

        User reload = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("matkhaumoi456", reload.getPassword())).isTrue();
    }

    @Test
    void changePassword_wrongOldPassword_throwsWrongOldPassword() {
        User user = registerUser("sai.matkhau.cu@example.com", "matkhaudung1", "Sai Mật Khẩu Cũ");

        assertThatThrownBy(() -> userAccountService.changePassword(
                user.getId(), new UpdatePassReq("matkhausai999", "matkhaumoi456")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("WRONG_OLD_PASSWORD"));
    }

    @Test
    void forgotPassword_existingAndNonExistingEmail_neitherThrows_onlyExistingEmailCreatesToken() {
        registerUser("ton.tai@example.com", "matkhaudung1", "Email Tồn Tại");

        // Cả 2 nhánh đều return void, không throw — verify bằng cách gọi không bắt exception.
        authService.forgotPassword(new ForgotPasswordRequest("ton.tai@example.com"));
        authService.forgotPassword(new ForgotPasswordRequest("khong.ton.tai@example.com"));

        assertThat(otpRepository.findByTypeAndEmail(OtpType.PASSWORD_RESET_OTP, "ton.tai@example.com")).isPresent();
        assertThat(otpRepository.findByTypeAndEmail(OtpType.PASSWORD_RESET_OTP, "khong.ton.tai@example.com")).isEmpty();
    }

    /**
     * Mã gửi cho người dùng phải là 6 chữ số vì họ gõ tay từ email vào màn hình điện thoại. Test
     * này bắt đúng chuỗi đi ra notifier — ba test còn lại tự ghi đè token_hash nên không canh
     * được định dạng, và bản trước đây sinh chuỗi Base64 43 ký tự mà không gì phát hiện ra.
     */
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

        // Khi token hết hạn hoặc bị xóa trong Redis:
        otpRepository.deleteByTypeAndEmail(OtpType.PASSWORD_RESET_OTP, "het.han@example.com");

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest("het.han@example.com", "123456", "matkhaumoi789")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("RESET_CODE_INVALID"));
    }
}

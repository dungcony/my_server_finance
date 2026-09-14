package com.datn.financeapp.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.auth.dto.request.RegisterRequest;
import com.datn.financeapp.auth.dto.request.VerifyEmailRequest;
import com.datn.financeapp.auth.enums.OtpType;
import com.datn.financeapp.auth.repository.OtpRepository;
import com.datn.financeapp.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.auth.service.AuthService;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.user.dto.request.UpdateMeRequest;
import com.datn.financeapp.user.dto.request.UpdatePassReq;
import com.datn.financeapp.user.dto.response.UserProfileResponse;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.enums.UserPlan;
import com.datn.financeapp.user.repository.UserRepository;
import com.datn.financeapp.user.service.AccountService;
import com.datn.financeapp.user.service.ProfileService;
import com.datn.financeapp.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Test tích hợp cho hồ sơ người dùng (User Profile) và đổi mật khẩu (Change Password).
 * Thuộc domain user (/users/me, /users/me/password).
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(TestRedisConfig.class)
class UserProfileIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci11c2VyLXByb2ZpbGUtdGVzdC0zMmI=");
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
    void getMe_existingUser_returnsProfile() {
        User user = registerUser("xem.ho.so@example.com", "matkhaudung1", "Xem Hồ Sơ");

        UserProfileResponse detail = userProfileService.getMe(user.getId());

        assertThat(detail.id()).isEqualTo(user.getId());
        assertThat(detail.email()).isEqualTo("xem.ho.so@example.com");
        assertThat(detail.plan()).isEqualTo(UserPlan.FREE);
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
        assertThat(reload.getEmail()).isEqualTo("cap.nhat@example.com");
        assertThat(reload.getPlan()).isEqualTo(UserPlan.FREE);
    }

    @Test
    void changePassword_correctOldPassword_revokesAllActiveRefreshTokens() {
        User user = registerUser("doi.matkhau@example.com", "matkhaucu123", "Đổi Mật Khẩu");

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
    void changePassword_sameAsOldPassword_throwsNewPasswordSameAsOld() {
        User user = registerUser("doi.trung.mk@example.com", "matkhaudung1", "Đổi Trùng Mật Khẩu");

        assertThatThrownBy(() -> userAccountService.changePassword(
                user.getId(), new UpdatePassReq("matkhaudung1", "matkhaudung1")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("NEW_PASSWORD_SAME_AS_OLD"));
    }
}

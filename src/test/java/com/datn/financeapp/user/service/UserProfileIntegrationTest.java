package com.datn.financeapp.user.service;

import com.datn.financeapp.user.dto.response.UserRes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.auth.dto.request.EmailLoginRequest;
import com.datn.financeapp.auth.dto.request.RegisterRequest;
import com.datn.financeapp.auth.dto.request.VerifyEmailRequest;
import com.datn.financeapp.auth.enums.OtpType;
import com.datn.financeapp.auth.repository.OtpRepository;
import com.datn.financeapp.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.auth.service.AuthService;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.mail.EmailService;
import com.datn.financeapp.user.dto.request.UpdateProfileRequest;
import com.datn.financeapp.user.dto.request.UpdatePassReq;

import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.enums.UserPlan;
import com.datn.financeapp.user.repository.UserRepository;
import com.datn.financeapp.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import java.time.Instant;

/**
 * Test tích hợp cho hồ sơ người dùng (User Profile) và đổi mật khẩu (Change Password).
 * Thuộc domain user (/users/me, /users/me/password).
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(TestRedisConfig.class)
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
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
    private com.datn.financeapp.auth.service.LoginService<com.datn.financeapp.auth.dto.request.EmailLoginRequest> loginService;

    @Autowired
    private UserBehavierService userBehavierService;

    @Autowired
    private UserService userService;

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
    void getMe_existingUser_returnsProfile() {
        User user = registerUser("xem.ho.so@example.com", "matkhaudung1", "Xem Hồ Sơ");

        UserRes detail = userBehavierService.getMe(user.getId());

        assertThat(detail.id()).isEqualTo(user.getId());
        assertThat(detail.email()).isEqualTo("xem.ho.so@example.com");
        assertThat(detail.plan()).isEqualTo(UserPlan.FREE);
    }

    @Test
    void getMe_emailAccount_hasPasswordTrue() {
        User user = registerUser("co.mat.khau@example.com", "matkhaudung1", "Có Mật Khẩu");

        assertThat(userBehavierService.getMe(user.getId()).hasPassword()).isTrue();
    }

    @Test
    void getMe_googleOnlyAccount_hasPasswordFalse() {
        var google = userService.resolveGoogleUser("chua.co.mk@example.com", "sub-chua-co-mk", Instant.now());

        assertThat(userBehavierService.getMe(google.id()).hasPassword()).isFalse();
    }

    @Test
    void generatePassword_googleOnlyAccount_mailsPasswordThatLogsIn_andFlipsHasPassword() {
        var google = userService.resolveGoogleUser("tao.mat.khau@example.com", "sub-tao-mk", Instant.now());

        userBehavierService.createPassword(google.id());

        ArgumentCaptor<String> rawPassword = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendGeneratedPassword(eq("tao.mat.khau@example.com"), rawPassword.capture());
        var login = loginService.login(
                new EmailLoginRequest("tao.mat.khau@example.com", rawPassword.getValue()),
                new com.datn.financeapp.auth.helper.ClientInfo("127.0.0.1", "junit"));
        assertThat(login.user().id()).isEqualTo(google.id());
        assertThat(userBehavierService.getMe(google.id()).hasPassword()).isTrue();
        // chỉ lưu bản băm, không lưu mật khẩu thô
        User reload = userRepository.findById(google.id()).orElseThrow();
        assertThat(reload.getPassword()).isNotEqualTo(rawPassword.getValue());
        assertThat(passwordEncoder.matches(rawPassword.getValue(), reload.getPassword())).isTrue();
    }

    @Test
    void generatePassword_calledTwice_secondCallThrowsPasswordAlreadySet() {
        var google = userService.resolveGoogleUser("goi.hai.lan@example.com", "sub-goi-hai-lan", Instant.now());
        userBehavierService.createPassword(google.id());

        assertThatThrownBy(() -> userBehavierService.createPassword(google.id()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("AUTH_PASSWORD_ALREADY_SET"));
        // mail chỉ gửi đúng một lần, mật khẩu đầu tiên vẫn còn hiệu lực
        verify(emailService, times(1)).sendGeneratedPassword(eq("goi.hai.lan@example.com"), anyString());
    }

    @Test
    void generatePassword_emailAccountWithPassword_throwsAndKeepsOldPassword() {
        User user = registerUser("da.co.mk@example.com", "matkhaudung1", "Đã Có Mật Khẩu");

        assertThatThrownBy(() -> userBehavierService.createPassword(user.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("AUTH_PASSWORD_ALREADY_SET"));

        User reload = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("matkhaudung1", reload.getPassword())).isTrue();
        verify(emailService, never()).sendGeneratedPassword(anyString(), anyString());
    }

    @Test
    void patchMe_UpdatesUsername_Succeeds() {
        User user = registerUser("cap.nhat@example.com", "matkhaudung1", "Tên Cũ");

        var result = userBehavierService.updateMe(user.getId(), new UpdateProfileRequest("Minh", "Nguyễn", null));

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

        loginService.login(
                new EmailLoginRequest("doi.matkhau@example.com", "matkhaucu123"),
                new com.datn.financeapp.auth.helper.ClientInfo("127.0.0.1", "junit"));
        loginService.login(
                new EmailLoginRequest("doi.matkhau@example.com", "matkhaucu123"),
                new com.datn.financeapp.auth.helper.ClientInfo("127.0.0.1", "junit"));

        assertThat(refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(user.getId())).isNotEmpty();

        userBehavierService.changePassword(user.getId(), new UpdatePassReq("matkhaucu123", "matkhaumoi456"));

        assertThat(refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(user.getId())).isEmpty();

        User reload = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("matkhaumoi456", reload.getPassword())).isTrue();
    }

    @Test
    void changePassword_wrongOldPassword_throwsWrongOldPassword() {
        User user = registerUser("sai.matkhau.cu@example.com", "matkhaudung1", "Sai Mật Khẩu Cũ");

        assertThatThrownBy(() -> userBehavierService.changePassword(
                user.getId(), new UpdatePassReq("matkhausai999", "matkhaumoi456")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("AUTH_OLD_PASSWORD_INCORRECT"));
    }

    @Test
    void changePassword_sameAsOldPassword_throwsNewPasswordSameAsOld() {
        User user = registerUser("doi.trung.mk@example.com", "matkhaudung1", "Đổi Trùng Mật Khẩu");

        assertThatThrownBy(() -> userBehavierService.changePassword(
                user.getId(), new UpdatePassReq("matkhaudung1", "matkhaudung1")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("AUTH_PASSWORD_SAME_AS_OLD"));
    }
}






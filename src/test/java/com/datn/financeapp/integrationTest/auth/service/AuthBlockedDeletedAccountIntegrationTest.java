package com.datn.financeapp.integrationTest.auth.service;

import com.datn.financeapp.module.auth.service.*;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datn.financeapp.module.auth.dto.response.AuthRes;
import com.datn.financeapp.module.auth.helper.ClientInfo;
import com.datn.financeapp.module.auth.dto.request.ForgotPasswordRequest;
import com.datn.financeapp.module.auth.dto.request.EmailLoginRequest;
import com.datn.financeapp.module.auth.dto.request.RefreshRequest;
import com.datn.financeapp.module.auth.dto.request.RegisterRequest;
import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.module.auth.dto.request.ResetPasswordRequest;
import com.datn.financeapp.module.auth.dto.request.VerifyEmailRequest;
import com.datn.financeapp.module.auth.enums.OtpType;
import com.datn.financeapp.module.auth.repository.OtpRepository;
import com.datn.financeapp.module.auth.service.AuthService;
import com.datn.financeapp.module.auth.service.LoginService;
import com.datn.financeapp.module.user.entity.User;
import com.datn.financeapp.module.user.enums.UserPlan;
import com.datn.financeapp.module.user.enums.RoleName;
import com.datn.financeapp.module.auth.repository.LoginAttemptRepository;
import com.datn.financeapp.module.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.module.user.enums.UserStatus;
import com.datn.financeapp.module.user.repository.UserRepository;
import com.datn.financeapp.module.user.service.UserBehavierService;
import com.datn.financeapp.common.mail.EmailService;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.module.wallet.repository.WalletRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
 * Bước B3 + B4 của prd/01 — tài khoản {@code is_blocked}/{@code is_deleted} bị chặn ở CẢ BỐN cửa
 * vào hệ thống, và phản hồi trả thêm {@code is_confirm}/{@code role}.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(TestRedisConfig.class)
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
class AuthBlockedDeletedAccountIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1ibG9ja2VkLWFjY291bnQtdGVzdA==");
    }

    private static final String PASSWORD = "matkhaudung1";

    @Autowired
    private AuthService authService;

    @Autowired
    private LoginService<EmailLoginRequest> loginService;

    @Autowired
    private UserBehavierService userBehavierService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private OtpRepository otpRepository;

    @Autowired
    private LoginAttemptRepository loginAttemptRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoSpyBean
    private EmailService emailService;

    @BeforeEach
    void cleanTables() {
        otpRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        loginAttemptRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    // -----------------------------------------------------------------
    // Cửa 1 — POST /auth/login
    // -----------------------------------------------------------------

    @Test
    void login_blockedAccountWithCorrectPassword_returnsAccountBlocked() {
        register("bi.admin.khoa@example.com");
        markUser("bi.admin.khoa@example.com", u -> u.setBlocked(true));

        assertThatThrownBy(() -> login("bi.admin.khoa@example.com", PASSWORD))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    assertThat(((BusinessException) ex).getCode()).isEqualTo("AUTH_ACCOUNT_BLOCKED");
                    assertThat(((BusinessException) ex).getHttpStatus()).isEqualTo(403);
                });
    }

    @Test
    void login_blockedAccountWithWrongPassword_returnsInvalidCredentials() {
        register("khoa.sai.mk@example.com");
        markUser("khoa.sai.mk@example.com", u -> u.setBlocked(true));

        assertThatThrownBy(() -> login("khoa.sai.mk@example.com", "sai-mat-khau"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("AUTH_CREDENTIALS_INVALID"));
    }

    @Test
    void login_deletedAccountWithCorrectPassword_returnsNotFound() {
        register("da.xoa@example.com");
        markUser("da.xoa@example.com", u -> u.setIsDeleted(true));

        assertThatThrownBy(() -> login("da.xoa@example.com", PASSWORD))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    assertThat(((BusinessException) ex).getCode()).isEqualTo("AUTH_CREDENTIALS_INVALID");
                    assertThat(((BusinessException) ex).getHttpStatus()).isEqualTo(401);
                });
    }

    // -----------------------------------------------------------------
    // Cửa 2 — POST /auth/refresh
    // -----------------------------------------------------------------

    @Test
    void refresh_tokenIssuedBeforeBlocking_isRejected() {
        AuthRes session = register("refresh.bi.khoa@example.com");
        markUser("refresh.bi.khoa@example.com", u -> u.setBlocked(true));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(session.token().refresh())))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("AUTH_ACCOUNT_BLOCKED"));
    }

    @Test
    void refresh_tokenOfDeletedAccount_isRejected() {
        AuthRes session = register("refresh.da.xoa@example.com");
        markUser("refresh.da.xoa@example.com", u -> u.setIsDeleted(true));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(session.token().refresh())))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("AUTH_REFRESH_TOKEN_INVALID"));
    }

    // -----------------------------------------------------------------
    // Cửa 3 — POST /auth/forgot-password
    // -----------------------------------------------------------------

    @Test
    void forgotPassword_blockedAccount_returnsAccountBlockedAndSendsNothing() {
        register("quen.mk.bi.khoa@example.com");
        markUser("quen.mk.bi.khoa@example.com", u -> u.setBlocked(true));

        assertThatThrownBy(() -> authService.forgotPassword(new ForgotPasswordRequest("quen.mk.bi.khoa@example.com")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "AUTH_ACCOUNT_BLOCKED");

        assertThat(otpRepository.findByTypeAndEmail(OtpType.PASSWORD_RESET_OTP, "quen.mk.bi.khoa@example.com")).isEmpty();
        Mockito.verify(emailService, Mockito.never())
                .sendPasswordResetCode(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    void forgotPassword_deletedAccount_returnsNotFoundAndSendsNothing() {
        register("quen.mk.da.xoa@example.com");
        markUser("quen.mk.da.xoa@example.com", u -> u.setIsDeleted(true));

        assertThatThrownBy(() -> authService.forgotPassword(new ForgotPasswordRequest("quen.mk.da.xoa@example.com")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "NOT_FOUND");

        assertThat(otpRepository.findByTypeAndEmail(OtpType.PASSWORD_RESET_OTP, "quen.mk.da.xoa@example.com")).isEmpty();
        Mockito.verify(emailService, Mockito.never())
                .sendPasswordResetCode(Mockito.anyString(), Mockito.anyString());
    }

    // -----------------------------------------------------------------
    // Cửa 4 — POST /auth/reset-password
    // -----------------------------------------------------------------

    @Test
    void resetPassword_codeIssuedBeforeBlocking_isRejectedAndPasswordUnchanged() {
        User user = registerAndReload("dat.lai.bi.khoa@example.com");
        String rawCode = issueResetCodeFor("dat.lai.bi.khoa@example.com");
        markUser("dat.lai.bi.khoa@example.com", u -> u.setBlocked(true));

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest("dat.lai.bi.khoa@example.com", rawCode, "matkhaumoi789")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("AUTH_ACCOUNT_BLOCKED"));

        User reload = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches(PASSWORD, reload.getPassword())).isTrue();
    }

    @Test
    void resetPassword_codeOfDeletedAccount_isRejectedAndPasswordUnchanged() {
        User user = registerAndReload("dat.lai.da.xoa@example.com");
        String rawCode = issueResetCodeFor("dat.lai.da.xoa@example.com");
        markUser("dat.lai.da.xoa@example.com", u -> u.setIsDeleted(true));

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest("dat.lai.da.xoa@example.com", rawCode, "matkhaumoi789")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("NOT_FOUND"));

        User reload = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches(PASSWORD, reload.getPassword())).isTrue();
    }

    // -----------------------------------------------------------------
    // B4 — ba trường mới trong phản hồi
    // -----------------------------------------------------------------

    @Test
    void verifiedAccount_returnsIsConfirmTrueAndRoleUser() {
        AuthRes response = register("truong.moi@example.com");

        assertThat(response.user().isConfirm()).isTrue();
        assertThat(response.user().roles()).extracting(r -> r.name()).contains(RoleName.ROLE_USER);
    }

    @Test
    void getMe_returnsProfileDetails() {
        User user = registerAndReload("me.day.du@example.com");

        var me = userBehavierService.getMe(user.getId());

        assertThat(me.email()).isEqualTo("me.day.du@example.com");
        assertThat(me.plan()).isEqualTo(UserPlan.FREE);
    }

    @Test
    void login_confirmedAccount_succeedsAndCarriesIsConfirmTrue() {
        register("da.xac.thuc@example.com");
        markUser("da.xac.thuc@example.com", u -> u.setStatus(UserStatus.ACTIVE));

        AuthRes response = login("da.xac.thuc@example.com", PASSWORD);

        assertThat(response.user().isConfirm()).isTrue();
        assertThat(response.user().roles()).extracting(r -> r.name()).contains(RoleName.ROLE_USER);
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private AuthRes register(String email) {
        authService.register(new RegisterRequest(email, PASSWORD, "Người Kiểm Thử"));
        String code = otpRepository
                .findByTypeAndEmail(OtpType.REGISTER_OTP, email)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy mã OTP đăng ký cho " + email))
                .getCode();
        authService.verifyEmail(new VerifyEmailRequest(email, code));
        return login(email, PASSWORD);
    }

    private User registerAndReload(String email) {
        register(email);
        return userRepository.findByEmail(email).orElseThrow();
    }

    private AuthRes login(String email, String password) {
        return loginService.login(new EmailLoginRequest(email, password), new ClientInfo("127.0.0.1", "junit"));
    }

    private void markUser(String email, Consumer<User> mutation) {
        User user = userRepository.findByEmail(email).orElseThrow();
        mutation.accept(user);
        userRepository.save(user);
    }

    private String issueResetCodeFor(String email) {
        authService.forgotPassword(new ForgotPasswordRequest(email));
        return otpRepository.findByTypeAndEmail(OtpType.PASSWORD_RESET_OTP, email.toLowerCase().trim())
                .orElseThrow()
                .getCode();
    }

    private static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

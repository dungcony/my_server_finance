package com.datn.financeapp.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datn.financeapp.auth.dto.response.AuthResponse;
import com.datn.financeapp.auth.dto.request.ForgotPasswordRequest;
import com.datn.financeapp.auth.dto.request.LoginRequest;
import com.datn.financeapp.auth.dto.request.RefreshRequest;
import com.datn.financeapp.auth.dto.request.RegisterRequest;
import com.datn.financeapp.auth.dto.request.ResetPasswordRequest;
import com.datn.financeapp.auth.entity.PasswordResetToken;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.auth.repository.LoginAttemptRepository;
import com.datn.financeapp.auth.repository.PasswordResetTokenRepository;
import com.datn.financeapp.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.user.repository.UserRepository;
import com.datn.financeapp.auth.service.AuthService;
import com.datn.financeapp.common.mail.EmailService;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.wallet.repository.WalletRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
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
 *
 * <p>Bốn cửa chứ không phải một: trước đợt này chỉ đăng nhập được nhắc tới trong tài liệu phân
 * quyền, nên {@code forgot-password} gọi {@code findByEmail} không lọc gì — tài khoản đã xoá vẫn
 * đặt lại mật khẩu và quay lại được. File này canh cả bốn để lỗ hổng đó không mở lại.
 *
 * <p>Gọi thẳng {@link AuthService} như {@code AuthProfilePasswordIntegrationTest}: mã lỗi và
 * trạng thái HTTP đi kèm nằm trong {@link BusinessException}, không cần dựng HTTP để đọc.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
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
    private com.datn.financeapp.user.service.UserProfileService userProfileService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private LoginAttemptRepository loginAttemptRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoSpyBean
    private EmailService emailService;

    @BeforeEach
    void cleanTables() {
        passwordResetTokenRepository.deleteAll();
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
                    assertThat(((BusinessException) ex).getCode()).isEqualTo("ACCOUNT_BLOCKED");
                    assertThat(((BusinessException) ex).getHttpStatus()).isEqualTo(403);
                });
    }

    /**
     * Mật khẩu SAI của một tài khoản bị khoá vẫn phải trả {@code INVALID_CREDENTIALS}: người
     * không biết mật khẩu thì cũng không đáng được biết tài khoản đó tồn tại và đang bị khoá.
     */
    @Test
    void login_blockedAccountWithWrongPassword_returnsInvalidCredentials() {
        register("khoa.sai.mk@example.com");
        markUser("khoa.sai.mk@example.com", u -> u.setBlocked(true));

        assertThatThrownBy(() -> login("khoa.sai.mk@example.com", "sai-mat-khau"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("INVALID_CREDENTIALS"));
    }

    /**
     * Tài khoản đã xoá KHÔNG được trả {@code ACCOUNT_BLOCKED} — với thế giới bên ngoài nó phải
     * giống hệt một email chưa từng đăng ký, kể cả khi gõ đúng mật khẩu cũ.
     */
    @Test
    void login_deletedAccountWithCorrectPassword_returnsInvalidCredentialsNotBlocked() {
        register("da.xoa@example.com");
        markUser("da.xoa@example.com", u -> u.setDeleted(true));

        assertThatThrownBy(() -> login("da.xoa@example.com", PASSWORD))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    assertThat(((BusinessException) ex).getCode()).isEqualTo("INVALID_CREDENTIALS");
                    assertThat(((BusinessException) ex).getHttpStatus()).isEqualTo(401);
                });
    }

    // -----------------------------------------------------------------
    // Cửa 2 — POST /auth/refresh
    // -----------------------------------------------------------------

    /**
     * Cửa dễ quên nhất: refresh token phát TRƯỚC khi ADMIN khoá vẫn nằm trong máy người dùng và
     * sống 30 ngày. Không chặn ở đây thì họ xin access token mới mãi và việc khoá vô nghĩa.
     */
    @Test
    void refresh_tokenIssuedBeforeBlocking_isRejected() {
        AuthResponse session = register("refresh.bi.khoa@example.com");
        markUser("refresh.bi.khoa@example.com", u -> u.setBlocked(true));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(session.refreshToken())))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("REFRESH_TOKEN_INVALID"));
    }

    @Test
    void refresh_tokenOfDeletedAccount_isRejected() {
        AuthResponse session = register("refresh.da.xoa@example.com");
        markUser("refresh.da.xoa@example.com", u -> u.setDeleted(true));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(session.refreshToken())))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("REFRESH_TOKEN_INVALID"));
    }

    // -----------------------------------------------------------------
    // Cửa 3 — POST /auth/forgot-password
    // -----------------------------------------------------------------

    /**
     * Vẫn không throw (Controller trả 200) nhưng KHÔNG sinh token và KHÔNG gọi notifier — trả
     * lỗi ở đây sẽ cho kẻ xấu một cách dò xem email nào đã bị khoá.
     */
    @Test
    void forgotPassword_blockedAccount_silentlyDoesNothing() {
        register("quen.mk.bi.khoa@example.com");
        markUser("quen.mk.bi.khoa@example.com", u -> u.setBlocked(true));

        assertThatCode(() -> authService.forgotPassword(new ForgotPasswordRequest("quen.mk.bi.khoa@example.com")))
                .doesNotThrowAnyException();

        assertThat(passwordResetTokenRepository.findAll()).isEmpty();
        Mockito.verify(emailService, Mockito.never())
                .sendPasswordResetCode(Mockito.anyString(), Mockito.anyString());
    }

    // Chính là lỗ hổng đã vá: tài khoản đã xoá từng nhận được mã và đặt lại mật khẩu thành công.
    @Test
    void forgotPassword_deletedAccount_silentlyDoesNothing() {
        register("quen.mk.da.xoa@example.com");
        markUser("quen.mk.da.xoa@example.com", u -> u.setDeleted(true));

        assertThatCode(() -> authService.forgotPassword(new ForgotPasswordRequest("quen.mk.da.xoa@example.com")))
                .doesNotThrowAnyException();

        assertThat(passwordResetTokenRepository.findAll()).isEmpty();
        Mockito.verify(emailService, Mockito.never())
                .sendPasswordResetCode(Mockito.anyString(), Mockito.anyString());
    }

    // -----------------------------------------------------------------
    // Cửa 4 — POST /auth/reset-password
    // -----------------------------------------------------------------

    /**
     * Mã phát hợp lệ TRƯỚC khi ADMIN khoá vẫn phải bị từ chối tại thời điểm dùng — chặn ở cửa 3
     * là chưa đủ, vì mã sống thêm 15 phút sau khi phát.
     */
    @Test
    void resetPassword_codeIssuedBeforeBlocking_isRejectedAndPasswordUnchanged() {
        User user = registerAndReload("dat.lai.bi.khoa@example.com");
        String rawCode = issueResetCodeFor("dat.lai.bi.khoa@example.com");
        markUser("dat.lai.bi.khoa@example.com", u -> u.setBlocked(true));

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest(rawCode, "matkhaumoi789")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("RESET_CODE_INVALID"));

        User reload = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches(PASSWORD, reload.getPasswordHash())).isTrue();
    }

    @Test
    void resetPassword_codeOfDeletedAccount_isRejectedAndPasswordUnchanged() {
        User user = registerAndReload("dat.lai.da.xoa@example.com");
        String rawCode = issueResetCodeFor("dat.lai.da.xoa@example.com");
        markUser("dat.lai.da.xoa@example.com", u -> u.setDeleted(true));

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest(rawCode, "matkhaumoi789")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("RESET_CODE_INVALID"));

        User reload = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches(PASSWORD, reload.getPasswordHash())).isTrue();
    }

    // -----------------------------------------------------------------
    // B4 — ba trường mới trong phản hồi
    // -----------------------------------------------------------------

    @Test
    void register_returnsUsernameIsConfirmFalseAndRoleUser() {
        AuthResponse response = register("truong.moi@example.com");

        assertThat(response.user().username()).isEqualTo("Người Kiểm Thử");
        // Luồng xác thực email chưa làm (prd/01 mục 11) nên đăng ký bằng email luôn ra false.
        assertThat(response.user().isConfirm()).isFalse();
        assertThat(response.user().role()).isEqualTo("USER");
    }

    @Test
    void getMe_returnsUsernameIsConfirmAndRole() {
        User user = registerAndReload("me.day.du@example.com");

        var me = userProfileService.getMe(user.getId());

        assertThat(me.username()).isEqualTo("Người Kiểm Thử");
        assertThat(me.isConfirm()).isFalse();
        assertThat(me.role()).isEqualTo("USER");
    }

    // Tài khoản backfill {@code is_confirm = TRUE} (V12) phải đi thẳng qua chứ không bị chặn.
    @Test
    void login_confirmedAccount_succeedsAndCarriesIsConfirmTrue() {
        register("da.xac.thuc@example.com");
        markUser("da.xac.thuc@example.com", u -> u.setConfirm(true));

        AuthResponse response = login("da.xac.thuc@example.com", PASSWORD);

        assertThat(response.user().isConfirm()).isTrue();
        assertThat(response.user().role()).isEqualTo("USER");
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private AuthResponse register(String email) {
        authService.register(new RegisterRequest(email, PASSWORD, "Người Kiểm Thử"));
        return login(email, PASSWORD);
    }

    private User registerAndReload(String email) {
        register(email);
        return userRepository.findByEmail(email).orElseThrow();
    }

    private AuthResponse login(String email, String password) {
        return authService.login(new LoginRequest(email, password), "127.0.0.1", "junit");
    }

    // Đặt cờ trạng thái tay, đúng như ADMIN (hoặc luồng xoá tài khoản nhóm C) sẽ làm sau này.
    private void markUser(String email, Consumer<User> mutation) {
        User user = userRepository.findByEmail(email).orElseThrow();
        mutation.accept(user);
        userRepository.save(user);
    }

    /**
     * Phát một mã đặt lại rồi ghi đè {@code token_hash} bằng chuỗi test tự chọn — cùng cách
     * {@code AuthProfilePasswordIntegrationTest} dùng, vì mã thật chỉ đi ra notifier chứ không
     * lưu dạng rõ ở đâu cả.
     */
    private String issueResetCodeFor(String email) {
        authService.forgotPassword(new ForgotPasswordRequest(email));
        PasswordResetToken token = passwordResetTokenRepository.findAll().get(0);
        String rawCode = "test-raw-reset-code-" + UUID.randomUUID();
        token.setTokenHash(sha256Hex(rawCode));
        passwordResetTokenRepository.save(token);
        return rawCode;
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

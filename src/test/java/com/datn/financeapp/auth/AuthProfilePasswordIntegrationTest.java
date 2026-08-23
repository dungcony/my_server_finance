package com.datn.financeapp.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datn.financeapp.auth.dto.ChangePasswordRequest;
import com.datn.financeapp.auth.dto.ForgotPasswordRequest;
import com.datn.financeapp.auth.dto.RegisterRequest;
import com.datn.financeapp.auth.dto.ResetPasswordRequest;
import com.datn.financeapp.auth.dto.UpdateProfileRequest;
import com.datn.financeapp.auth.dto.UserDetailDto;
import com.datn.financeapp.auth.entity.PasswordResetToken;
import com.datn.financeapp.auth.entity.RefreshToken;
import com.datn.financeapp.auth.entity.User;
import com.datn.financeapp.auth.repository.PasswordResetTokenRepository;
import com.datn.financeapp.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.auth.repository.UserRepository;
import com.datn.financeapp.auth.service.AuthService;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.wallet.repository.WalletRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
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
 * Test tích hợp gọi trực tiếp {@link AuthService} (Testcontainers PostgreSQL thật) cho AUTH-05
 * (profile) và AUTH-06 (đổi/quên/đặt lại mật khẩu). Nhất quán cách tiếp cận Plan 04 Task 1 — test
 * Service layer, Controller wiring verify riêng ở Task 2 bằng test HTTP thật.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
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
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanTables() {
        passwordResetTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User registerUser(String email, String password, String fullName) {
        authService.register(new RegisterRequest(email, password, fullName));
        return userRepository.findByEmail(email).orElseThrow();
    }

    @Test
    void patchMe_UpdatesFullName_Succeeds() {
        User user = registerUser("cap.nhat@example.com", "matkhaudung1", "Tên Cũ");

        var result = authService.updateProfile(user.getId(), new UpdateProfileRequest("Tên Mới", null));

        assertThat(result.fullName()).isEqualTo("Tên Mới");
        User reload = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reload.getFullName()).isEqualTo("Tên Mới");
        // UpdateProfileRequest không có field email/plan -> record chỉ 2 tham số, không có cách
        // nào gọi truyền email/plan qua đây (verify bằng compile-time, xem field list ở class).
        assertThat(reload.getEmail()).isEqualTo("cap.nhat@example.com");
        assertThat(reload.getPlan()).isEqualTo("free");
    }

    @Test
    void getMe_NoTransactionsOrGroupsYet_returnsStatsDefaultingToZero() {
        User user = registerUser("xem.ho.so@example.com", "matkhaudung1", "Xem Hồ Sơ");

        UserDetailDto detail = authService.getMe(user.getId());

        assertThat(detail.stats().walletCount()).isEqualTo(1L); // ví Tiền mặt tạo lúc đăng ký
        assertThat(detail.stats().transactionCount()).isEqualTo(0L);
        assertThat(detail.stats().groupCount()).isEqualTo(0L);
    }

    @Test
    void changePassword_correctOldPassword_revokesAllActiveRefreshTokens() {
        User user = registerUser("doi.matkhau@example.com", "matkhaucu123", "Đổi Mật Khẩu");

        // Giả lập 2 phiên đăng nhập trước đó — mỗi login cấp thêm 1 refresh token active.
        authService.login(
                new com.datn.financeapp.auth.dto.LoginRequest("doi.matkhau@example.com", "matkhaucu123"),
                "127.0.0.1", "junit");
        authService.login(
                new com.datn.financeapp.auth.dto.LoginRequest("doi.matkhau@example.com", "matkhaucu123"),
                "127.0.0.1", "junit");

        assertThat(refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(user.getId())).isNotEmpty();

        authService.changePassword(user.getId(), new ChangePasswordRequest("matkhaucu123", "matkhaumoi456"));

        assertThat(refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(user.getId())).isEmpty();

        User reload = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("matkhaumoi456", reload.getPasswordHash())).isTrue();
    }

    @Test
    void changePassword_wrongOldPassword_throwsWrongOldPassword() {
        User user = registerUser("sai.matkhau.cu@example.com", "matkhaudung1", "Sai Mật Khẩu Cũ");

        assertThatThrownBy(() -> authService.changePassword(
                        user.getId(), new ChangePasswordRequest("matkhausai999", "matkhaumoi456")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("WRONG_OLD_PASSWORD"));
    }

    @Test
    void forgotPassword_existingAndNonExistingEmail_neitherThrows_onlyExistingEmailCreatesToken() {
        registerUser("ton.tai@example.com", "matkhaudung1", "Email Tồn Tại");

        // Cả 2 nhánh đều return void, không throw — verify bằng cách gọi không bắt exception.
        authService.forgotPassword(new ForgotPasswordRequest("ton.tai@example.com"));
        authService.forgotPassword(new ForgotPasswordRequest("khong.ton.tai@example.com"));

        assertThat(passwordResetTokenRepository.findAll()).hasSize(1);
    }

    @Test
    void resetPassword_validUnexpiredCode_succeeds_reuseOnSecondCallIsRejected() {
        User user = registerUser("dat.lai@example.com", "matkhaucu123", "Đặt Lại Mật Khẩu");
        authService.forgotPassword(new ForgotPasswordRequest("dat.lai@example.com"));

        PasswordResetToken savedToken = passwordResetTokenRepository.findAll().get(0);
        String rawResetCode = extractRawResetCodeFromLog(savedToken);

        authService.resetPassword(new ResetPasswordRequest(rawResetCode, "matkhaumoi789"));

        User reload = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("matkhaumoi789", reload.getPasswordHash())).isTrue();

        PasswordResetToken reloadToken = passwordResetTokenRepository.findById(savedToken.getId()).orElseThrow();
        assertThat(reloadToken.getUsedAt()).isNotNull();

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest(rawResetCode, "khac123456")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("RESET_CODE_INVALID"));
    }

    @Test
    void resetPassword_expiredCode_returnsResetCodeInvalid() {
        registerUser("het.han@example.com", "matkhaucu123", "Hết Hạn");
        authService.forgotPassword(new ForgotPasswordRequest("het.han@example.com"));

        PasswordResetToken savedToken = passwordResetTokenRepository.findAll().get(0);
        String rawResetCode = extractRawResetCodeFromLog(savedToken);

        // Giả lập hết hạn: ghi thẳng expiresAt trong quá khứ qua repository trước khi gọi API.
        savedToken.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        passwordResetTokenRepository.save(savedToken);

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest(rawResetCode, "matkhaumoi789")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("RESET_CODE_INVALID"));
    }

    /**
     * Test không có quyền truy cập log — thay vào đó tự sinh lại đúng thuật toán sha256Hex như
     * AuthService, ghi thẳng token_hash mới khớp raw code test tự chọn để verify hành vi
     * reset-password mà không phụ thuộc bắt log output.
     */
    private String extractRawResetCodeFromLog(PasswordResetToken savedToken) {
        String rawResetCode = "test-raw-reset-code-" + UUID.randomUUID();
        savedToken.setTokenHash(sha256Hex(rawResetCode));
        passwordResetTokenRepository.save(savedToken);
        return rawResetCode;
    }

    private static String sha256Hex(String raw) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

package com.datn.financeapp.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.auth.dto.request.RegisterRequest;
import com.datn.financeapp.auth.dto.request.VerifyEmailRequest;
import com.datn.financeapp.auth.enums.OtpType;
import com.datn.financeapp.auth.repository.LoginAttemptRepository;
import com.datn.financeapp.auth.repository.OtpRepository;
import com.datn.financeapp.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.auth.service.AuthService;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.repository.UserRepository;
import com.datn.financeapp.user.service.ProfileService;
import com.datn.financeapp.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Collections;
import java.util.UUID;

/**
 * Test tích hợp cho chức năng xoá tài khoản người dùng (DELETE /users/me).
 * Thuộc domain user.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(TestRedisConfig.class)
class UserDeleteAccountIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1kZWxldGUtYWNjb3VudC10ZXN0");
    }

    private static final String PASSWORD = "matkhaudung1";

    @Autowired
    private AuthService authService;

    @Autowired
    private ProfileService userProfileService;

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
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        otpRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        loginAttemptRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
        SecurityContextHolder.clearContext();
    }

    private User registerUser(String email) {
        authService.register(new RegisterRequest(email, PASSWORD, "Test User"));
        String code = otpRepository
                .findByTypeAndEmail(OtpType.REGISTER_OTP, email)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy mã OTP đăng ký cho " + email))
                .getCode();
        authService.verifyEmail(new VerifyEmailRequest(email, code));
        return userRepository.findByEmail(email).orElseThrow();
    }

    private void authenticateAs(UUID userId) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList()));
        SecurityContextHolder.setContext(context);
    }

    @Test
    void deleteMe_withValidPassword_setsIsDeletedAndKillsRefreshTokens() {
        User user = registerUser("xoa.thanh.cong@example.com");
        authenticateAs(user.getId());

        userProfileService.deleteMe(PASSWORD);

        User reload = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reload.isDeleted()).isTrue();
    }

    @Test
    void deleteMe_withWrongPassword_rejectsAndLeavesAccountIntact() {
        User user = registerUser("xoa.sai.pass@example.com");
        authenticateAs(user.getId());

        assertThatThrownBy(() -> userProfileService.deleteMe("sai-mat-khau"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("WRONG_PASSWORD"));

        User reload = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reload.isDeleted()).isFalse();
    }

    @Test
    void deleteMe_googleAccountWithoutPassword_canDeleteWithoutPassword() {
        User user = registerUser("google.no.pass@example.com");
        user.setPassword(null);
        user.setGoogleId("google-sub-12345");
        userRepository.save(user);
        authenticateAs(user.getId());

        userProfileService.deleteMe(null);

        User reload = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reload.isDeleted()).isTrue();
    }
}

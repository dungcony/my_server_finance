package com.datn.financeapp.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datn.financeapp.auth.dto.response.AuthResponse;
import com.datn.financeapp.auth.dto.request.ForgotPasswordRequest;
import com.datn.financeapp.auth.dto.request.LoginRequest;
import com.datn.financeapp.auth.dto.request.RefreshRequest;
import com.datn.financeapp.auth.dto.request.RegisterRequest;
import com.datn.financeapp.user.dto.request.DeleteAccountRequest;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.auth.repository.LoginAttemptRepository;
import com.datn.financeapp.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.user.repository.UserRepository;
import com.datn.financeapp.auth.service.AuthService;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.user.service.AccountService;
import com.datn.financeapp.user.service.ProfileService;
import com.datn.financeapp.wallet.repository.WalletRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Nhóm C của prd/01 — xoá tài khoản (C2) và giới hạn gửi mã đặt lại theo email (C4).
 *
 * <p>Gọi thẳng {@link AuthService} như các file test auth khác: mã lỗi và HTTP status nằm sẵn
 * trong {@link BusinessException}, không cần dựng tầng HTTP để đọc.
 *
 * <p>Các test C4 dùng email KHÁC NHAU ở mỗi test: bộ đếm nằm trong một bean singleton dùng chung
 * cả lớp, cửa sổ một giờ nên lượt đã tiêu không tự hồi giữa hai test.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class AuthDeleteAccountIntegrationTest {

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
    private AccountService userAccountService;

    @Autowired
    private ProfileService userProfileService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private LoginAttemptRepository loginAttemptRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        // groups.created_by_id là ON DELETE RESTRICT — không dọn hai bảng nhóm trước thì
        // userRepository.deleteAll() ném lỗi ràng buộc khoá ngoại.
        jdbcTemplate.update("DELETE FROM group_members");
        jdbcTemplate.update("DELETE FROM groups");
        refreshTokenRepository.deleteAll();
        loginAttemptRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    private void deleteAccountAs(User user, String password) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(user.getId().toString(), null, Collections.emptyList()));
        SecurityContextHolder.setContext(context);
        try {
            userProfileService.deleteMe(password);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // -----------------------------------------------------------------
    // C2 — DELETE /auth/account -> DELETE /users/me
    // -----------------------------------------------------------------

    @Test
    void deleteAccount_wrongPassword_isRejectedAndAccountStaysActive() {
        User user = registerAndReload("xoa.sai.mk@example.com");

        assertThatThrownBy(() -> deleteAccountAs(user, "sai-mat-khau"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    assertThat(((BusinessException) ex).getCode()).isEqualTo("WRONG_PASSWORD");
                    assertThat(((BusinessException) ex).getHttpStatus()).isEqualTo(400);
                });

        assertThat(userRepository.findById(user.getId()).orElseThrow().isDeleted()).isFalse();
    }

    @Test
    void deleteAccount_correctPassword_marksDeletedAndRevokesAllRefreshTokens() {
        AuthResponse session = register("xoa.dung.mk@example.com");
        User user = userRepository.findByEmail("xoa.dung.mk@example.com").orElseThrow();

        deleteAccountAs(user, PASSWORD);

        assertThat(userRepository.findById(user.getId()).orElseThrow().isDeleted()).isTrue();
        assertThat(refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(user.getId())).isEmpty();
        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(session.refreshToken())))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("REFRESH_TOKEN_INVALID"));
    }

    /**
     * Dữ liệu tài chính GIỮ NGUYÊN. Ví "Tiền mặt" do luồng đăng ký tạo là bằng chứng gần nhất:
     * xoá cứng sẽ kéo theo CASCADE quét sạch nó cùng mọi giao dịch.
     */
    @Test
    void deleteAccount_keepsWalletsAndFinancialData() {
        User user = registerAndReload("xoa.giu.vi@example.com");

        deleteAccountAs(user, PASSWORD);

        assertThat(walletRepository.countByUserIdAndIsDeletedFalse(user.getId())).isEqualTo(1);
    }

    // Bước 3 của bốn bước: rời mọi nhóm đang tham gia, nhưng bản ghi thành viên vẫn còn.
    @Test
    void deleteAccount_deactivatesGroupMembershipsWithoutDeletingRows() {
        User user = registerAndReload("xoa.roi.nhom@example.com");
        UUID groupId = insertGroupOwnedBy(user.getId());

        deleteAccountAs(user, PASSWORD);

        Boolean stillActive = jdbcTemplate.queryForObject(
                "SELECT is_active FROM group_members WHERE group_id = ? AND user_id = ?",
                Boolean.class, groupId, user.getId());
        assertThat(stillActive).isFalse();
    }

    @Test
    void login_afterAccountDeleted_returnsInvalidCredentials() {
        User user = registerAndReload("xoa.roi.dang.nhap@example.com");
        deleteAccountAs(user, PASSWORD);

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("xoa.roi.dang.nhap@example.com", PASSWORD), "127.0.0.1", "junit"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("INVALID_CREDENTIALS"));
    }

    /**
     * Email KHÔNG được giải phóng — cố ý: cho đăng ký lại bằng email cũ sẽ khiến người mới thừa
     * hưởng lịch sử nhóm của người trước.
     */
    @Test
    void register_withEmailOfDeletedAccount_isStillRejected() {
        User user = registerAndReload("xoa.roi.dang.ky.lai@example.com");
        deleteAccountAs(user, PASSWORD);

        assertThatThrownBy(() -> register("xoa.roi.dang.ky.lai@example.com"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    assertThat(((BusinessException) ex).getCode()).isEqualTo("EMAIL_ALREADY_EXISTS");
                    assertThat(((BusinessException) ex).getHttpStatus()).isEqualTo(409);
                });
    }

    // -----------------------------------------------------------------
    // C4 — giới hạn forgot-password theo email
    // -----------------------------------------------------------------

    @Test
    void forgotPassword_sixthRequestForSameEmailWithinAnHour_isRateLimited() {
        String email = "doi.bom@example.com";
        register(email);

        for (int i = 0; i < 5; i++) {
            assertThatCode(() -> authService.forgotPassword(new ForgotPasswordRequest(email)))
                    .doesNotThrowAnyException();
        }

        assertThatThrownBy(() -> authService.forgotPassword(new ForgotPasswordRequest(email)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    assertThat(((BusinessException) ex).getCode()).isEqualTo("RATE_LIMIT_EXCEEDED");
                    assertThat(((BusinessException) ex).getHttpStatus()).isEqualTo(429);
                });
    }

    // Bộ đếm theo email, không theo lời gọi: sáu email khác nhau không đụng trần của nhau.
    @Test
    void forgotPassword_sixDifferentEmails_areNotRateLimited() {
        for (int i = 0; i < 6; i++) {
            String email = "khac.nhau." + i + "@example.com";
            register(email);
            assertThatCode(() -> authService.forgotPassword(new ForgotPasswordRequest(email)))
                    .doesNotThrowAnyException();
        }
    }

    /**
     * Lượt bị tiêu kể cả khi email chưa từng đăng ký. Nếu chỉ đếm lúc gửi mail thật thì chênh
     * lệch số lượt còn lại trở thành một cách dò danh sách người dùng.
     */
    @Test
    void forgotPassword_unknownEmail_stillConsumesQuota() {
        String email = "chua.dang.ky@example.com";

        for (int i = 0; i < 5; i++) {
            authService.forgotPassword(new ForgotPasswordRequest(email));
        }

        assertThatThrownBy(() -> authService.forgotPassword(new ForgotPasswordRequest(email)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("RATE_LIMIT_EXCEEDED"));
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private AuthResponse register(String email) {
        authService.register(new RegisterRequest(email, PASSWORD, "Người Kiểm Thử"));
        return authService.login(new LoginRequest(email, PASSWORD), "127.0.0.1", "junit");
    }

    private User registerAndReload(String email) {
        register(email);
        return userRepository.findByEmail(email).orElseThrow();
    }

    // Chưa có module group/ (Phase 5 backend) nên dựng dữ liệu nhóm bằng SQL thô.
    private UUID insertGroupOwnedBy(UUID userId) {
        UUID groupId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO groups (id, name, created_by_id, invite_code, invite_code_expires_at) "
                        + "VALUES (?, ?, ?, ?, now() + INTERVAL '7 days')",
                groupId, "Nhà mình", userId, UUID.randomUUID().toString().substring(0, 8));
        jdbcTemplate.update(
                "INSERT INTO group_members (id, group_id, user_id, role) VALUES (?, ?, ?, 'owner')",
                UUID.randomUUID(), groupId, userId);
        return groupId;
    }
}

package com.datn.financeapp.user.controller;

import com.datn.financeapp.auth.dto.request.LoginRequest;
import com.datn.financeapp.auth.dto.request.RegisterRequest;
import com.datn.financeapp.auth.dto.response.AuthResponse;
import com.datn.financeapp.auth.repository.LoginAttemptRepository;
import com.datn.financeapp.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.auth.service.AuthService;
import com.datn.financeapp.common.ratelimit.RateLimitFilter;
import com.datn.financeapp.user.dto.request.BlockUserRequest;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.enums.UserStatus;
import com.datn.financeapp.user.repository.UserRepository;
import com.datn.financeapp.wallet.repository.WalletRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import com.datn.financeapp.common.security.BlacklistedUserRepository;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminBlockUserIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1hZG1pbi1ibG9jay11c2VyLXRlc3QtMzJi");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @TestConfiguration
    static class NoRateLimitConfig {
        @Bean
        @Primary
        RateLimitFilter rateLimitFilter() {
            return new RateLimitFilter(null) {
                @Override
                protected void doFilterInternal(
                        HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                        throws ServletException, IOException {
                    chain.doFilter(req, res);
                }
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private LoginAttemptRepository loginAttemptRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BlacklistedUserRepository blacklistedUserRepository;

    private static final String PASSWORD = "Password123!";

    @BeforeEach
    void setup() {
        refreshTokenRepository.deleteAll();
        loginAttemptRepository.deleteAll();
        walletRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM user_roles");
        userRepository.deleteAll();
    }

    private User createAndVerifyUser(String email) {
        authService.register(new RegisterRequest(email, PASSWORD));
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    private void assignAdminRole(UUID userId) {
        jdbcTemplate.update(
                "INSERT INTO user_roles (user_id, role_id) SELECT ?, r.id FROM roles r WHERE r.name = 'ROLE_ADMIN' ON CONFLICT DO NOTHING",
                userId
        );
    }

    private String loginAndGetToken(String email) {
        AuthResponse response = authService.login(new LoginRequest(email, PASSWORD), "127.0.0.1", "junit");
        return response.accessToken();
    }

    @Test
    @DisplayName("TC_USR_01: Admin khóa tài khoản người dùng thành công")
    void admin_blockUser_success() throws Exception {
        // Given: Tạo admin và target user
        User admin = createAndVerifyUser("admin@example.com");
        assignAdminRole(admin.getId());
        String adminToken = loginAndGetToken("admin@example.com");

        User target = createAndVerifyUser("target@example.com");
        String targetToken = loginAndGetToken("target@example.com");

        // Đảm bảo target user có refresh token
        assertThat(refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(target.getId())).isNotEmpty();

        BlockUserRequest req = new BlockUserRequest(target.getId(), "Vi phạm điều khoản sử dụng");

        // When: Admin gọi PATCH /admin/user/block (userId nằm trong body, không phải trên đường dẫn)
        mockMvc.perform(patch("/admin/user/block")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.msg").value("Khóa tài khoản người dùng thành công"));

        // Then:
        // 1. Target user status trong DB là BLOCKED
        User updatedTarget = userRepository.findById(target.getId()).orElseThrow();
        assertThat(updatedTarget.getStatus()).isEqualTo(UserStatus.BLOCKED);

        // 2. Refresh token của target user đã bị revoke
        assertThat(refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(target.getId())).isEmpty();

        // 3. Redis đã đánh dấu blacklist cho user này
        assertThat(blacklistedUserRepository.isBlacklisted(target.getId().toString())).isTrue();

        // 4. Target user dùng token cũ gọi API -> Bị chặn 403 ACCOUNT_BLOCKED
        mockMvc.perform(get("/users/me")
                        .header("Authorization", "Bearer " + targetToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_BLOCKED"));
    }

    @Test
    @DisplayName("TC_USR_02: Người dùng thường không có quyền admin gọi API block -> 403 Forbidden")
    void normalUser_blockUser_forbidden() throws Exception {
        User normalUser = createAndVerifyUser("user@example.com");
        String normalUserToken = loginAndGetToken("user@example.com");

        User target = createAndVerifyUser("target2@example.com");
        BlockUserRequest req = new BlockUserRequest(target.getId(), "Cố tình phá hoại");

        mockMvc.perform(patch("/admin/user/block")
                        .header("Authorization", "Bearer " + normalUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("TC_USR_03: Admin khóa người dùng không tồn tại -> 404 Not Found")
    void admin_blockNonExistentUser_notFound() throws Exception {
        User admin = createAndVerifyUser("admin2@example.com");
        assignAdminRole(admin.getId());
        String adminToken = loginAndGetToken("admin2@example.com");

        UUID nonExistentId = UUID.randomUUID();
        BlockUserRequest req = new BlockUserRequest(nonExistentId, "Test 404");

        mockMvc.perform(patch("/admin/user/block")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("TC_USR_04: Admin tự khóa chính mình -> 400 Bad Request")
    void admin_blockSelf_badRequest() throws Exception {
        User admin = createAndVerifyUser("admin3@example.com");
        assignAdminRole(admin.getId());
        String adminToken = loginAndGetToken("admin3@example.com");

        BlockUserRequest req = new BlockUserRequest(admin.getId(), "Tự khóa mình");

        mockMvc.perform(patch("/admin/user/block")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("TC_USR_05: Bỏ trống lý do khóa vẫn khóa được — lý do là tuỳ chọn")
    void admin_blockUser_blankReason_succeeds() throws Exception {
        User admin = createAndVerifyUser("admin4@example.com");
        assignAdminRole(admin.getId());
        String adminToken = loginAndGetToken("admin4@example.com");

        User target = createAndVerifyUser("target4@example.com");
        BlockUserRequest req = new BlockUserRequest(target.getId(), "");

        // `reason` chỉ có @Size(max = 500), không bắt buộc: admin được khoá nhanh mà không phải
        // giải trình. Lý do (nếu có) đi vào blacklist Redis và log để tra ngược về sau.
        mockMvc.perform(patch("/admin/user/block")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        User updated = userRepository.findById(target.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(UserStatus.BLOCKED);
    }
}

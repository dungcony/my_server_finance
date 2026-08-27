package com.datn.financeapp.notification;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datn.financeapp.auth.entity.User;
import com.datn.financeapp.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.auth.repository.UserRepository;
import com.datn.financeapp.common.ratelimit.RateLimitFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * api/11-THONG-BAO.md — GET /notifications và PATCH /notifications/{id}/read. Quyền D-27 kiểm
 * tra ngay trong SQL (T-04-01), 404 khi không có quyền (T-04-02, không phải 403). Chưa có
 * endpoint tạo thông báo ở Phase 4 plan này — chèn dữ liệu giả trực tiếp bằng {@link
 * JdbcTemplate} để kiểm GET/PATCH, theo đúng khuôn {@code WalletAccessControlIntegrationTest}.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1ub3RpZmljYXRpb24tdGVzdC0zMmI=");
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
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.update("DELETE FROM notifications");
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String registerAndGetAccessToken(String email) throws Exception {
        Map<String, Object> body = Map.of(
                "email", email,
                "password", "matkhau123",
                "full_name", "Người Kiểm Thử Thông Báo");
        String response = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Map<?, ?> parsed = objectMapper.readValue(response, Map.class);
        Map<?, ?> data = (Map<?, ?>) parsed.get("data");
        return (String) data.get("access_token");
    }

    private UUID userIdOf(String email) {
        User user = userRepository.findByEmail(email).orElseThrow();
        return user.getId();
    }

    private UUID insertNotification(UUID userId, String type, boolean isRead) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO notifications (id, user_id, type, title, content, reference_id, is_read) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                id,
                userId,
                type,
                "Tiêu đề kiểm thử",
                "Nội dung kiểm thử",
                UUID.randomUUID(),
                isRead);
        return id;
    }

    @Test
    void getNotifications_returnsOnlyOwnNotifications() throws Exception {
        String tokenA = registerAndGetAccessToken("nguoi.a.list@example.com");
        String tokenB = registerAndGetAccessToken("nguoi.b.list@example.com");
        UUID userIdA = userIdOf("nguoi.a.list@example.com");
        UUID userIdB = userIdOf("nguoi.b.list@example.com");

        insertNotification(userIdA, "budget_alert", false);
        insertNotification(userIdA, "debt_reminder", true);
        insertNotification(userIdB, "budget_alert", false);

        mockMvc.perform(get("/notifications").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.pagination.total_items").value(2));
    }

    @Test
    void markRead_isIdempotent() throws Exception {
        String token = registerAndGetAccessToken("nguoi.markread@example.com");
        UUID userId = userIdOf("nguoi.markread@example.com");
        UUID notificationId = insertNotification(userId, "budget_alert", false);

        mockMvc.perform(patch("/notifications/" + notificationId + "/read")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Gọi lại lần 2 trên thông báo đã đọc — vẫn 200, không lỗi (idempotent).
        mockMvc.perform(patch("/notifications/" + notificationId + "/read")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Boolean isRead = jdbcTemplate.queryForObject(
                "SELECT is_read FROM notifications WHERE id = ?", Boolean.class, notificationId);
        org.junit.jupiter.api.Assertions.assertTrue(Boolean.TRUE.equals(isRead));
    }

    @Test
    void markRead_otherUsersNotification_returns404() throws Exception {
        String tokenA = registerAndGetAccessToken("nguoi.a.markread@example.com");
        String tokenB = registerAndGetAccessToken("nguoi.b.markread@example.com");
        UUID userIdB = userIdOf("nguoi.b.markread@example.com");
        UUID notificationOfB = insertNotification(userIdB, "budget_alert", false);

        mockMvc.perform(patch("/notifications/" + notificationOfB + "/read")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void getNotifications_filterByIsRead() throws Exception {
        String token = registerAndGetAccessToken("nguoi.filter@example.com");
        UUID userId = userIdOf("nguoi.filter@example.com");

        insertNotification(userId, "budget_alert", false);
        insertNotification(userId, "debt_reminder", true);
        insertNotification(userId, "recurring_generated", false);

        mockMvc.perform(get("/notifications")
                        .param("is_read", "false")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.pagination.total_items").value(2));
    }
}

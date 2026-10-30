package com.datn.financeapp.integrationTest.goal.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datn.financeapp.TestAuthSupport;
import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.module.auth.repository.OtpRepository;
import com.datn.financeapp.module.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.module.user.repository.UserRepository;
import com.datn.financeapp.common.ratelimit.RateLimitFilter;
import com.datn.financeapp.module.wallet.repository.WalletRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
 * Test tích hợp mục tiêu tiết kiệm độc lập (Goal Tracking không ví) — kiểm tra nạp tiền,
 * rút tiền, sửa/hủy đóng góp và trigger tự động cập nhật tiến độ.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import({TestRedisConfig.class, TestAuthSupport.class})
class GoalContributionIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1nb2FsLWNvbnRyaWJ1dGlvbi0zMmI");
    }

    @TestConfiguration
    static class NoRateLimitConfig {
        @Bean
        @Primary
        RateLimitFilter rateLimitFilter() {
            return new RateLimitFilter(null) {
                @Override
                protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
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
    private WalletRepository walletRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestAuthSupport authSupport;

    @Autowired
    private OtpRepository otpRepository;

    @BeforeEach
    void cleanTables() {
        otpRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM goal_contributions");
        jdbcTemplate.update("DELETE FROM savings_goals");
        jdbcTemplate.update("DELETE FROM transactions");
        refreshTokenRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ---------------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------------

    private String registerAndGetAccessToken(String email) throws Exception {
        return authSupport.registerAndGetAccessToken(email);
    }

    private String createGoal(String token, String name, long targetAmount, Long initialAmount, String note)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("target_amount", targetAmount);
        if (initialAmount != null) {
            body.put("initial_amount", initialAmount);
        }
        if (note != null) {
            body.put("note", note);
        }

        String response = mockMvc.perform(post("/goals")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Map<?, ?> data = (Map<?, ?>) objectMapper.readValue(response, Map.class).get("data");
        return (String) ((Map<?, ?>) data.get("goal")).get("id");
    }

    private Map<?, ?> addContribution(String token, String goalId, long amount, String note) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("amount", amount);
        if (note != null) {
            body.put("note", note);
        }

        String response = mockMvc.perform(post("/goals/" + goalId + "/contributions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return (Map<?, ?>) objectMapper.readValue(response, Map.class).get("data");
    }

    private Map<?, ?> withdraw(String token, String goalId, long amount, String note) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("amount", amount);
        if (note != null) {
            body.put("note", note);
        }

        String response = mockMvc.perform(post("/goals/" + goalId + "/withdraw")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return (Map<?, ?>) objectMapper.readValue(response, Map.class).get("data");
    }

    private long savedAmountFromDatabase(String goalId) {
        return jdbcTemplate.queryForObject(
                "SELECT saved_amount FROM savings_goals WHERE id = ?", Long.class, UUID.fromString(goalId));
    }

    private String statusFromDatabase(String goalId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM savings_goals WHERE id = ?", String.class, UUID.fromString(goalId));
    }

    // ---------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Tạo mục tiêu với số tiền ban đầu: trigger tự ghi nhận saved_amount, không sinh transaction")
    void createGoal_withInitialAmount_recordsSavedAmountWithoutTransactions() throws Exception {
        String token = registerAndGetAccessToken("muc.tieu.tao@example.com");
        String goalId = createGoal(token, "Mua xe máy", 50_000_000, 2_000_000L, "Nuôi heo đất");

        // Tiến độ ban đầu được trigger tính toán
        assertThat(savedAmountFromDatabase(goalId)).isEqualTo(2_000_000L);
        assertThat(statusFromDatabase(goalId)).isEqualTo("IN_PROGRESS");

        // Không có giao dịch ví nào sinh ra
        Integer txnCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class);
        assertThat(txnCount).isZero();
    }

    @Test
    @DisplayName("Nạp tiền vào mục tiêu: tiến độ tăng và chuyển sang completed khi đủ target")
    void addContribution_updatesSavedAmountAndCompletesWhenTargetReached() throws Exception {
        String token = registerAndGetAccessToken("muc.tieu.nap@example.com");
        String goalId = createGoal(token, "Mua tai nghe", 2_000_000, null, "Heo đất");

        Map<?, ?> res = addContribution(token, goalId, 2_000_000, "Thưởng tuần");

        assertThat(savedAmountFromDatabase(goalId)).isEqualTo(2_000_000L);
        assertThat(statusFromDatabase(goalId)).isEqualTo("COMPLETED");

        Map<?, ?> goalProgress = (Map<?, ?>) res.get("goal");
        assertThat(goalProgress.get("status")).isEqualTo("COMPLETED");
        assertThat(((Number) goalProgress.get("saved_amount")).longValue()).isEqualTo(2_000_000L);
    }

    @Test
    @DisplayName("Rút bớt tiền khi có việc gấp: tiến độ giảm và mở lại trạng thái in_progress")
    void withdraw_reducesSavedAmountAndReopensStatus() throws Exception {
        String token = registerAndGetAccessToken("muc.tieu.rut@example.com");
        String goalId = createGoal(token, "Mua máy giặt", 10_000_000, 10_000_000L, "Sổ tiết kiệm");

        assertThat(statusFromDatabase(goalId)).isEqualTo("COMPLETED");

        // Rút 2.000.000đ khi có việc gấp
        Map<?, ?> res = withdraw(token, goalId, 2_000_000, "Cần tiền sửa xe");

        assertThat(savedAmountFromDatabase(goalId)).isEqualTo(8_000_000L);
        assertThat(statusFromDatabase(goalId)).isEqualTo("IN_PROGRESS");

        Map<?, ?> goalProgress = (Map<?, ?>) res.get("goal");
        assertThat(goalProgress.get("status")).isEqualTo("IN_PROGRESS");
        assertThat(((Number) goalProgress.get("saved_amount")).longValue()).isEqualTo(8_000_000L);
    }

    @Test
    @DisplayName("Rút bớt tiền vượt quá số tiền hiện tích lũy -> trả về lỗi 400")
    void withdraw_exceedsSavedAmount_returns400() throws Exception {
        String token = registerAndGetAccessToken("muc.tieu.rut.qua@example.com");
        String goalId = createGoal(token, "Mua điện thoại", 10_000_000, 1_000_000L, null);

        Map<String, Object> body = Map.of(
                "amount", 2_000_000,
                "note", "Rút quá số dư"
        );

        mockMvc.perform(post("/goals/" + goalId + "/withdraw")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("GOAL_WITHDRAW_EXCEEDS_SAVED"));

        // Số tiền giữ nguyên
        assertThat(savedAmountFromDatabase(goalId)).isEqualTo(1_000_000L);
    }

    @Test
    @DisplayName("Sửa khoản đóng góp: trigger tự tính toán lại số dư sau khi điều chỉnh")
    void updateContribution_adjustsSavedAmount() throws Exception {
        String token = registerAndGetAccessToken("muc.tieu.sua@example.com");
        String goalId = createGoal(token, "Đi du lịch", 10_000_000, null, null);

        Map<?, ?> first = addContribution(token, goalId, 3_000_000, "Lần 1");
        String contribId = (String) ((Map<?, ?>) first.get("contribution")).get("id");

        // Người dùng phát hiện ghi nhầm 3 triệu thành 5 triệu -> sửa thành 5 triệu
        Map<String, Object> updateBody = Map.of(
                "amount", 5_000_000,
                "note", "Sửa lại số tiền đúng"
        );

        mockMvc.perform(patch("/goals/" + goalId + "/contributions/" + contribId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goal.saved_amount").value(5_000_000));

        assertThat(savedAmountFromDatabase(goalId)).isEqualTo(5_000_000L);
    }

    @Test
    @DisplayName("Hủy đợt đóng góp: trigger tự trừ tiền và mở lại trạng thái in_progress")
    void cancelContribution_reopensCompletedToInProgress() throws Exception {
        String token = registerAndGetAccessToken("muc.tieu.rut.lai@example.com");
        String goalId = createGoal(token, "Mua điện thoại", 5_000_000, null, null);

        // Hai lần nạp 2.500.000 -> vừa đủ target -> trigger tự đặt completed
        addContribution(token, goalId, 2_500_000, "Lần 1");
        Map<?, ?> second = addContribution(token, goalId, 2_500_000, "Lần 2");

        assertThat(savedAmountFromDatabase(goalId)).isEqualTo(5_000_000L);
        assertThat(statusFromDatabase(goalId)).isEqualTo("COMPLETED");

        String contributionId = (String) ((Map<?, ?>) second.get("contribution")).get("id");

        mockMvc.perform(delete("/goals/" + goalId + "/contributions/" + contributionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Trigger tự trừ lại và tự mở lại trạng thái
        assertThat(savedAmountFromDatabase(goalId)).isEqualTo(2_500_000L);
        assertThat(statusFromDatabase(goalId)).isEqualTo("IN_PROGRESS");
    }
}

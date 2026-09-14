package com.datn.financeapp.report.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datn.financeapp.TestAuthSupport;
import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.user.repository.UserRepository;
import com.datn.financeapp.common.ratelimit.RateLimitFilter;
import com.datn.financeapp.wallet.repository.WalletRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
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
 * 5 test case cho REPORT-05/D-43/D-44 (api/06-BAO-CAO.md mục 7) — Task 3 plan 04-06: vòng đời
 * 202 -> processing -> completed, pdf/excel trả 501 sạch (không tạo rác {@code export_jobs}),
 * IDOR và hết hạn đều chặn đúng.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import({TestRedisConfig.class, TestAuthSupport.class})
class ExportJobIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1leHBvcnQtdGVzdC0zMmI=");
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
    private com.datn.financeapp.auth.repository.OtpRepository otpRepository;

    @BeforeEach
    void cleanTables() {
        // OTP nằm ở Redis, không bị Testcontainers PostgreSQL dọn hộ.
        otpRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM export_jobs");
        jdbcTemplate.update("DELETE FROM transactions");
        refreshTokenRepository.deleteAll();
        walletRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM categories WHERE user_id IS NOT NULL");
        userRepository.deleteAll();
    }

    // ---------------------------------------------------------------- helpers

    private String registerAndGetAccessToken(String email) throws Exception {
        return authSupport.registerAndGetAccessToken(email);
    }

    private String createWallet(String token) throws Exception {
        Map<String, Object> body = Map.of("name", "Ví Xuất Báo Cáo", "type", "cash", "initial_balance", 1_000_000);
        String response = mockMvc.perform(post("/wallets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Map<?, ?> data = (Map<?, ?>) objectMapper.readValue(response, Map.class).get("data");
        return (String) data.get("id");
    }

    private UUID systemCategoryId(String type) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE user_id IS NULL AND type = ? LIMIT 1", UUID.class, type);
    }

    private String createExportJob(String token, String format) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("format", format);
        String response = mockMvc.perform(post("/reports/export")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Map<?, ?> data = (Map<?, ?>) objectMapper.readValue(response, Map.class).get("data");
        return (String) data.get("job_id");
    }

    private String pollUntilCompleted(String token, String jobId) throws Exception {
        String status = "processing";
        String response = null;
        for (int i = 0; i < 20 && "processing".equals(status); i++) {
            response = mockMvc.perform(get("/reports/export/" + jobId).header("Authorization", "Bearer " + token))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            Map<?, ?> data = (Map<?, ?>) objectMapper.readValue(response, Map.class).get("data");
            status = (String) data.get("status");
            if ("processing".equals(status)) {
                Thread.sleep(200);
            }
        }
        Assertions.assertEquals("completed", status, "Job phải hoàn tất trong thời gian chờ của test");
        return response;
    }

    // ------------------------------------------------------------------ tests

    @Test
    void createExportJob_returns202WithJobId() throws Exception {
        String token = registerAndGetAccessToken("xuat.bao.cao.tao@example.com");
        createWallet(token);

        Map<String, Object> body = Map.of("format", "csv");
        mockMvc.perform(post("/reports/export")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.job_id").exists())
                .andExpect(jsonPath("$.data.status").value("processing"));
    }

    @Test
    void pollExportJob_eventuallyCompletes() throws Exception {
        String token = registerAndGetAccessToken("xuat.bao.cao.poll@example.com");
        String walletId = createWallet(token);
        UUID categoryId = systemCategoryId("expense");

        Map<String, Object> txn = new HashMap<>();
        txn.put("type", "expense");
        txn.put("amount", 50_000);
        txn.put("wallet_id", walletId);
        txn.put("category_id", categoryId.toString());
        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(txn)))
                .andExpect(status().isCreated());

        String jobId = createExportJob(token, "csv");
        String finalResponse = pollUntilCompleted(token, jobId);
        Map<?, ?> data = (Map<?, ?>) objectMapper.readValue(finalResponse, Map.class).get("data");
        Assertions.assertNotNull(data.get("download_url"));
        Assertions.assertNotNull(data.get("expires_at"));
    }

    @Test
    void createExportJob_pdfFormat_returns501WithoutCreatingJob() throws Exception {
        String token = registerAndGetAccessToken("xuat.bao.cao.pdf@example.com");
        createWallet(token);

        Map<String, Object> body = Map.of("format", "pdf");
        mockMvc.perform(post("/reports/export")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.error.code").value("FORMAT_NOT_SUPPORTED"));

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM export_jobs", Integer.class);
        Assertions.assertEquals(0, count, "pdf chưa hỗ trợ KHÔNG được tạo bản ghi export_jobs");
    }

    @Test
    void downloadExport_otherUsersJob_returns404() throws Exception {
        String tokenA = registerAndGetAccessToken("xuat.bao.cao.chu.a@example.com");
        createWallet(tokenA);
        String jobId = createExportJob(tokenA, "csv");
        pollUntilCompleted(tokenA, jobId);

        String tokenB = registerAndGetAccessToken("xuat.bao.cao.chu.b@example.com");
        mockMvc.perform(get("/reports/export/" + jobId + "/download").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void downloadExport_expiredJob_returns410() throws Exception {
        String token = registerAndGetAccessToken("xuat.bao.cao.het.han@example.com");
        createWallet(token);
        String jobId = createExportJob(token, "csv");
        pollUntilCompleted(token, jobId);

        jdbcTemplate.update(
                "UPDATE export_jobs SET expires_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)),
                UUID.fromString(jobId));

        mockMvc.perform(get("/reports/export/" + jobId + "/download").header("Authorization", "Bearer " + token))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error.code").value("EXPORT_LINK_EXPIRED"));
    }
}

package com.datn.financeapp.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Test hành vi thật của {@link IdempotencyAspect} qua Testcontainers PostgreSQL — cần hành vi
 * UNIQUE constraint / ON CONFLICT thật, mock không chứng minh được (D-23).
 *
 * Giả lập Authentication giống hệt {@code JwtAuthFilter} thật: principal là chuỗi UUID trần
 * (không dùng {@code @WithMockUser} vì principal của nó là {@code UserDetails}, không khớp
 * với {@code SecurityContextUtil.currentUserId()} vốn parse trực tiếp {@code principal.toString()}).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdempotencyAspectIntegrationTest {

    private static final UUID FIXED_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Authentication FIXED_USER_AUTH =
            new UsernamePasswordAuthenticationToken(FIXED_USER_ID.toString(), null, Collections.emptyList());

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1pZGVtcG90ZW5jeS10ZXN0LTMyYg==");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdempotencyKeyRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetCounterAndCleanTable() {
        TestOnlyController.CALL_COUNT.set(0);
        TestOnlyController.throwOnNextCall = false;
        repository.deleteAll();
        // Tạo user tiền đề cho FK fk_idem_user bằng JdbcTemplate — UserRepository thật
        // chưa tồn tại tới Plan 04, và đây chỉ là dữ liệu setup, không phải nghiệp vụ.
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", FIXED_USER_ID);
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, full_name, plan, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, now())",
                FIXED_USER_ID,
                "idempotency-test@example.com",
                "$2a$12$placeholderplaceholderplaceholderplacehold",
                "Idempotency Test User",
                "free");
    }

    @TestConfiguration
    static class TestOnlyControllerConfig {
        @Bean
        TestOnlyController testOnlyController() {
            return new TestOnlyController();
        }
    }

    @RestController
    public static class TestOnlyController {
        static final AtomicInteger CALL_COUNT = new AtomicInteger(0);
        static volatile boolean throwOnNextCall = false;

        @Idempotent
        @PostMapping("/__test-only/idempotent-echo")
        public ResponseEntity<Map<String, Object>> echo() {
            int callNumber = CALL_COUNT.incrementAndGet();
            if (throwOnNextCall) {
                throwOnNextCall = false;
                throw new RuntimeException("Lỗi nghiệp vụ giả lập để test rollback idempotency");
            }
            return ResponseEntity.ok(Map.of("call_number", callNumber));
        }
    }

    @Test
    void calledTwiceWithSameKeyAfterCompletion_runsBusinessLogicOnlyOnce() throws Exception {
        String key = "key-completed-" + UUID.randomUUID();

        mockMvc.perform(post("/__test-only/idempotent-echo")
                        .with(authentication(FIXED_USER_AUTH))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.call_number").value(1));

        mockMvc.perform(post("/__test-only/idempotent-echo")
                        .with(authentication(FIXED_USER_AUTH))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.call_number").value(1));

        assertThat(TestOnlyController.CALL_COUNT.get()).isEqualTo(1);
    }

    @Test
    void calledAgainWhileProcessing_returns409RequestInProgress() throws Exception {
        String key = "key-processing-" + UUID.randomUUID();
        String endpoint = "POST /__test-only/idempotent-echo";

        IdempotencyKeyEntity processingRecord = IdempotencyKeyEntity.builder()
                .id(UUID.randomUUID())
                .idempotencyKey(key)
                .userId(FIXED_USER_ID)
                .endpoint(endpoint)
                .status("processing")
                .createdAt(Instant.now())
                .build();
        repository.save(processingRecord);

        mockMvc.perform(post("/__test-only/idempotent-echo")
                        .with(authentication(FIXED_USER_AUTH))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REQUEST_IN_PROGRESS"));

        assertThat(TestOnlyController.CALL_COUNT.get()).isEqualTo(0);
    }

    @Test
    void businessLogicThrows_deletesProcessingRecord_retryRunsNormallyAfterwards() throws Exception {
        String key = "key-exception-" + UUID.randomUUID();
        TestOnlyController.throwOnNextCall = true;

        mockMvc.perform(post("/__test-only/idempotent-echo")
                        .with(authentication(FIXED_USER_AUTH))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is5xxServerError());

        assertThat(repository.findAll()).isEmpty();

        mockMvc.perform(post("/__test-only/idempotent-echo")
                        .with(authentication(FIXED_USER_AUTH))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        assertThat(TestOnlyController.CALL_COUNT.get()).isEqualTo(2);
    }

    @Test
    void noIdempotencyKeyHeader_runsNormallyWithoutUsingTable() throws Exception {
        mockMvc.perform(post("/__test-only/idempotent-echo")
                        .with(authentication(FIXED_USER_AUTH))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        assertThat(TestOnlyController.CALL_COUNT.get()).isEqualTo(1);
        assertThat(repository.findAll()).isEmpty();
    }
}

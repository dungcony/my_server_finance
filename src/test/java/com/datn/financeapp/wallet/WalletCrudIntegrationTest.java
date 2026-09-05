package com.datn.financeapp.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datn.financeapp.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.auth.repository.UserRepository;
import com.datn.financeapp.common.ratelimit.RateLimitFilter;
import com.datn.financeapp.wallet.repository.WalletRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Test tích hợp qua HTTP thật (MockMvc + WalletController + WalletService + Testcontainers
 * PostgreSQL) cho CRUD ví — 6 behavior của Task 1 plan 02-02 (api/02-VI.md mục 1-7).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WalletCrudIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci13YWxsZXQtY3J1ZC10ZXN0LTMyYg==");
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
    private WalletRepository walletRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        // transactions tham chiếu wallets qua fk_txn_wallet/fk_txn_dest — phải xoá trước wallets,
        // nếu không mọi test có insertFakeTransaction() sẽ làm cleanTables() của lần chạy sau vỡ
        // vì còn bản ghi transactions treo lơ lửng tham chiếu ví đã bị walletRepository.deleteAll().
        jdbcTemplate.update("DELETE FROM transactions");
        refreshTokenRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String registerAndGetAccessToken(String email) throws Exception {
        Map<String, Object> body = Map.of(
                "email", email,
                "password", "matkhau123",
                "username", "Người Kiểm Thử");
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

    private String createWallet(String token, String name, String type, long initialBalance) throws Exception {
        Map<String, Object> body = Map.of(
                "name", name,
                "type", type,
                "initial_balance", initialBalance);
        String response = mockMvc.perform(post("/wallets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Map<?, ?> parsed = objectMapper.readValue(response, Map.class);
        Map<?, ?> data = (Map<?, ?>) parsed.get("data");
        return (String) data.get("id");
    }

    @Test
    void createWallet_newName_returns201WithCurrentBalanceEqualsInitialAndNoTransactions() throws Exception {
        String token = registerAndGetAccessToken("tao.vi@example.com");

        Map<String, Object> body = Map.of(
                "name", "Vietcombank",
                "type", "bank",
                "initial_balance", 5_000_000);

        mockMvc.perform(post("/wallets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.current_balance").value(5_000_000))
                .andExpect(jsonPath("$.data.name").value("Vietcombank"));

        // register() đã tạo sẵn ví "Tiền mặt" — tổng phải là 2, không có giao dịch nào phát sinh
        // từ việc tạo ví (đọc lại qua GET /wallets/summary để không tự ý query bảng transactions
        // trực tiếp trong test, giữ test ở tầng HTTP).
        mockMvc.perform(get("/wallets").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void createWallet_duplicateName_returns409WalletNameExists() throws Exception {
        String token = registerAndGetAccessToken("trung.ten@example.com");
        createWallet(token, "Ví Chung", "bank", 1_000_000);

        Map<String, Object> body = Map.of(
                "name", "VÍ CHUNG",
                "type", "cash",
                "initial_balance", 0);

        mockMvc.perform(post("/wallets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WALLET_NAME_EXISTS"));
    }

    @Test
    void patchWallet_withCurrentBalanceField_returns400BalanceNotEditable() throws Exception {
        String token = registerAndGetAccessToken("sua.so.du@example.com");
        String walletId = createWallet(token, "Ví Sửa", "bank", 1_000_000);

        Map<String, Object> body = Map.of("current_balance", 999_999);

        mockMvc.perform(patch("/wallets/" + walletId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BALANCE_NOT_EDITABLE"));
    }

    @Test
    void patchWallet_withTypeField_returns400BalanceNotEditable() throws Exception {
        String token = registerAndGetAccessToken("sua.loai@example.com");
        String walletId = createWallet(token, "Ví Loại", "bank", 1_000_000);

        Map<String, Object> body = Map.of("type", "cash");

        mockMvc.perform(patch("/wallets/" + walletId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BALANCE_NOT_EDITABLE"));
    }

    @Test
    void deleteWallet_hasTransactionsWithoutFlag_returns409WalletHasTransactions() throws Exception {
        String token = registerAndGetAccessToken("xoa.co.gd@example.com");
        String walletId = createWallet(token, "Ví Có Giao Dịch", "bank", 1_000_000);

        Map<?, ?> parsed = objectMapper.readValue(
                mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                Map.class);
        String userId = (String) ((Map<?, ?>) parsed.get("data")).get("id");

        insertFakeTransaction(userId, walletId);

        mockMvc.perform(delete("/wallets/" + walletId).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WALLET_HAS_TRANSACTIONS"));
    }

    @Test
    void deleteWallet_lastWallet_returns409CannotDeleteLastWallet() throws Exception {
        String token = registerAndGetAccessToken("vi.cuoi.cung@example.com");

        // register() đã tạo sẵn ví "Tiền mặt" — đây chính là ví cuối cùng, xoá phải bị chặn.
        Map<?, ?> parsed = objectMapper.readValue(
                mockMvc.perform(get("/wallets").header("Authorization", "Bearer " + token))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                Map.class);
        var wallets = (java.util.List<?>) parsed.get("data");
        String walletId = (String) ((Map<?, ?>) wallets.get(0)).get("id");

        mockMvc.perform(delete("/wallets/" + walletId).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CANNOT_DELETE_LAST_WALLET"));
    }

    @Test
    void reorderWallets_returnsSortOrderMatchingArrayPosition() throws Exception {
        String token = registerAndGetAccessToken("sap.xep@example.com");
        String walletA = createWallet(token, "Ví A", "bank", 0);
        String walletB = createWallet(token, "Ví B", "bank", 0);

        Map<String, Object> body = Map.of("sort_order", java.util.List.of(walletB, walletA));

        mockMvc.perform(patch("/wallets/reorder")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        var reloadedA = walletRepository.findById(java.util.UUID.fromString(walletA)).orElseThrow();
        var reloadedB = walletRepository.findById(java.util.UUID.fromString(walletB)).orElseThrow();

        assertThat(reloadedB.getSortOrder()).isEqualTo(0);
        assertThat(reloadedA.getSortOrder()).isEqualTo(1);
    }

    @Test
    void getSummary_noSharedWallets_returnsSharedTotalZero() throws Exception {
        String token = registerAndGetAccessToken("tong-quan@example.com");
        createWallet(token, "Ví Tổng Quan", "bank", 2_000_000);

        mockMvc.perform(get("/wallets/summary").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shared_total").value(0))
                .andExpect(jsonPath("$.data.shared_wallet_count").value(0));
    }

    /**
     * Chèn thẳng một giao dịch tối thiểu qua JDBC — bảng transactions/categories đã tồn tại từ
     * V1/V2/V5 dù service nghiệp vụ giao dịch thuộc Phase 3, chỉ cần đủ để COUNT(*) trong
     * WalletService.delete() thấy > 0. Dùng danh mục hệ thống (user_id IS NULL) đã có sẵn từ
     * V5__du_lieu_he_thong.sql.
     */
    private void insertFakeTransaction(String userId, String walletId) {
        String catId = jdbcTemplate.queryForObject(
                "SELECT id::text FROM categories WHERE user_id IS NULL AND type = 'expense' LIMIT 1", String.class);
        jdbcTemplate.update(
                "INSERT INTO transactions (id, user_id, wallet_id, category_id, type, amount, date, source) "
                        + "VALUES (gen_random_uuid(), ?::uuid, ?::uuid, ?::uuid, 'expense', 10000, CURRENT_DATE, 'manual')",
                userId, walletId, catId);
    }
}

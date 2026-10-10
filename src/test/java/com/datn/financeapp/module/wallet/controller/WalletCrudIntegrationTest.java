package com.datn.financeapp.module.wallet.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.util.List;
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
@org.springframework.context.annotation.Import({TestRedisConfig.class, TestAuthSupport.class})
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
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

    @Autowired
    private TestAuthSupport authSupport;

    @Autowired
    private OtpRepository otpRepository;

    @BeforeEach
    void cleanTables() {
        // OTP nằm ở Redis, không bị Testcontainers PostgreSQL dọn hộ — sót lại thì lần đăng ký
        // sau của cùng email sẽ đọc nhầm mã cũ.
        otpRepository.deleteAll();
        // transactions tham chiếu wallets qua fk_txn_wallet/fk_txn_dest — phải xoá trước wallets,
        // nếu không mọi test có insertFakeTransaction() sẽ làm cleanTables() của lần chạy sau vỡ
        // vì còn bản ghi transactions treo lơ lửng tham chiếu ví đã bị walletRepository.deleteAll().
        jdbcTemplate.update("DELETE FROM transactions");
        refreshTokenRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String registerAndGetAccessToken(String email) throws Exception {
        return authSupport.registerAndGetAccessToken(email);
    }

    // đọc danh sách ví qua HTTP rồi lấy id của ví đang có is_default = true
    private String findDefaultWalletId(String token) throws Exception {
        String response = mockMvc.perform(get("/wallets").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<?> wallets = (List<?>) objectMapper.readValue(response, Map.class).get("data");
        return wallets.stream()
                .map(w -> (Map<?, ?>) w)
                .filter(w -> Boolean.TRUE.equals(w.get("is_default")))
                .map(w -> (String) w.get("id"))
                .findFirst()
                .orElseThrow();
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

        // Trigger ở CSDL tạo sẵn ví "Tiền mặt" mặc định khi đăng ký, nên danh sách gồm ví đó và ví vừa tạo.
        // Đọc lại qua GET /wallets để giữ test ở tầng HTTP, không query thẳng bảng.
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
                .andExpect(jsonPath("$.error.code").value("WALLET_BALANCE_NOT_EDITABLE"));
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
                .andExpect(jsonPath("$.error.code").value("WALLET_BALANCE_NOT_EDITABLE"));
    }

    @Test
    void deleteWallet_hasTransactionsWithoutFlag_returns409WalletHasTransactions() throws Exception {
        String token = registerAndGetAccessToken("xoa.co.gd@example.com");
        String walletId = createWallet(token, "Ví Có Giao Dịch", "bank", 1_000_000);
        // Ví thứ hai để ví đang xoá KHÔNG phải ví cuối cùng — nếu không, luật
        // WALLET_LAST_NOT_DELETABLE chặn trước và test này không còn kiểm được điều nó định kiểm.
        createWallet(token, "Ví Dự Phòng", "cash", 0);

        Map<?, ?> parsed = objectMapper.readValue(
                mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
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
    void register_newUser_hasExactlyOneDefaultCashWallet() throws Exception {
        String token = registerAndGetAccessToken("vi.mac.dinh@example.com");

        mockMvc.perform(get("/wallets").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Tiền mặt"))
                .andExpect(jsonPath("$.data[0].type").value("cash"))
                .andExpect(jsonPath("$.data[0].is_default").value(true));
    }

    @Test
    void createWallet_additionalWallet_isNotDefault() throws Exception {
        String token = registerAndGetAccessToken("vi.them@example.com");
        String walletId = createWallet(token, "Ví Thêm", "bank", 0);

        mockMvc.perform(get("/wallets/" + walletId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.is_default").value(false));
    }

    @Test
    void patchWallet_setDefaultTrue_movesDefaultFlagToThatWallet() throws Exception {
        String token = registerAndGetAccessToken("doi.mac.dinh@example.com");
        String oldDefaultId = findDefaultWalletId(token);
        String walletId = createWallet(token, "Ví Mới Mặc Định", "bank", 0);

        mockMvc.perform(patch("/wallets/" + walletId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("is_default", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.is_default").value(true));

        assertThat(findDefaultWalletId(token)).isEqualTo(walletId).isNotEqualTo(oldDefaultId);
    }

    @Test
    void patchWallet_unsetDefaultDirectly_returns400DefaultRequired() throws Exception {
        String token = registerAndGetAccessToken("bo.mac.dinh@example.com");
        String defaultId = findDefaultWalletId(token);

        mockMvc.perform(patch("/wallets/" + defaultId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("is_default", false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("WALLET_DEFAULT_REQUIRED"));
    }

    @Test
    void deleteWallet_defaultWallet_returns409DefaultNotDeletable() throws Exception {
        String token = registerAndGetAccessToken("xoa.mac.dinh@example.com");
        // có ví thứ hai để chắc chắn lỗi đến từ luật ví mặc định chứ không phải "ví cuối cùng"
        createWallet(token, "Ví Phụ", "bank", 0);
        String defaultId = findDefaultWalletId(token);

        mockMvc.perform(delete("/wallets/" + defaultId).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WALLET_DEFAULT_NOT_DELETABLE"));
    }

    @Test
    void deleteWallet_afterMovingDefault_oldDefaultWalletCanBeDeleted() throws Exception {
        String token = registerAndGetAccessToken("xoa.sau.doi@example.com");
        String oldDefaultId = findDefaultWalletId(token);
        String newDefaultId = createWallet(token, "Ví Mặc Định Mới", "bank", 0);

        mockMvc.perform(patch("/wallets/" + newDefaultId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("is_default", true))))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/wallets/" + oldDefaultId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
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
                "SELECT id::text FROM categories WHERE NOT deleted LIMIT 1", String.class);
        jdbcTemplate.update(
                "INSERT INTO transactions (id, user_id, wallet_id, category_id, type, amount, date, source) "
                        + "VALUES (gen_random_uuid(), ?::uuid, ?::uuid, ?::uuid, 'EXPENSE', 10000, CURRENT_DATE, 'MANUAL')",
                userId, walletId, catId);
    }
}

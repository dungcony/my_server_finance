package com.datn.financeapp.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import java.util.HashMap;
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
 * Test tích hợp mục tiêu tiết kiệm (GOAL-01..04) — trọng tâm là RANH GIỚI TRIGGER và HAI CHẾ ĐỘ
 * NẠP TIỀN, mục ưu tiên số 2 của D-58.
 *
 * <p>Ca quan trọng nhất là {@link #cancelContribution_reopensCompletedToInProgress()}: trigger
 * {@code trg_goal_contributions_sync} phải tự MỞ LẠI trạng thái từ {@code completed} về
 * {@code in_progress} khi rút lại một lần nạp. Cũng như ở sổ nợ, test luôn đọc THẲNG từ CSDL để
 * đối chiếu — nếu backend tự ghi đè hai cột đó thì con số sẽ "đúng một cách tình cờ" và test
 * không phát hiện được gì.
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
    private com.datn.financeapp.auth.repository.OtpRepository otpRepository;

    @BeforeEach
    void cleanTables() {
        // OTP nằm ở Redis, không bị Testcontainers PostgreSQL dọn hộ.
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

    private String createWallet(String token, String name, long initialBalance) throws Exception {
        Map<String, Object> body = Map.of(
                "name", name,
                "type", "cash",
                "initial_balance", initialBalance);
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

    // Tạo mục tiêu; {@code walletId} và {@code initialAmount} cho phép null để bỏ qua.
    private String createGoal(String token, String name, long targetAmount, String walletId, Long initialAmount)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("target_amount", targetAmount);
        if (walletId != null) {
            body.put("wallet_id", walletId);
        }
        if (initialAmount != null) {
            body.put("initial_amount", initialAmount);
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

    private Map<?, ?> addContribution(
            String token, String goalId, long amount, String sourceWalletId, Boolean createTransaction)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("amount", amount);
        if (sourceWalletId != null) {
            body.put("source_wallet_id", sourceWalletId);
        }
        if (createTransaction != null) {
            body.put("create_transaction", createTransaction);
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

    private long walletBalance(String walletId) {
        return jdbcTemplate.queryForObject(
                "SELECT current_balance FROM wallets WHERE id = ?", Long.class, UUID.fromString(walletId));
    }

    private long savedAmountFromDatabase(String goalId) {
        return jdbcTemplate.queryForObject(
                "SELECT saved_amount FROM savings_goals WHERE id = ?", Long.class, UUID.fromString(goalId));
    }

    private String statusFromDatabase(String goalId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM savings_goals WHERE id = ?", String.class, UUID.fromString(goalId));
    }

    private int liveTransactionCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transactions WHERE is_deleted = false", Integer.class);
    }

    // ---------------------------------------------------------------------
    // Test
    // ---------------------------------------------------------------------

    /**
     * GOAL-01, api/09 mục B2 — {@code initial_amount} KHÔNG sinh giao dịch nào: đó là tiền người
     * dùng đã tích được từ trước, ghi thêm giao dịch sẽ trừ oan ví một lần nữa.
     *
     * <p>Tên test phản ánh đúng phạm vi khẳng định: {@code initial_amount} tự nó không sinh GIAO
     * DỊCH, dù vẫn sinh một bản ghi {@code goal_contributions} nội bộ để trigger cộng vào
     * {@code saved_amount} (nếu không thì tiến độ ban đầu người dùng nhập sẽ mất hẳn).
     */
    @Test
    void createGoal_withInitialAmount_doesNotCreateTransactionForInitialAmountItself() throws Exception {
        String token = registerAndGetAccessToken("muc.tieu.tao@example.com");
        String walletId = createWallet(token, "Ví Tiết Kiệm", 10_000_000);

        long balanceBefore = walletBalance(walletId);
        String goalId = createGoal(token, "Mua xe máy", 50_000_000, walletId, 2_000_000L);

        // Không một giao dịch nào được tạo — đây là khẳng định cốt lõi của api/09 mục B2.
        assertThat(liveTransactionCount()).isZero();
        // Số dư ví không hề bị đụng tới.
        assertThat(walletBalance(walletId)).isEqualTo(balanceBefore);
        // Nhưng tiến độ ban đầu vẫn được ghi nhận, và do TRIGGER tính chứ không phải backend.
        assertThat(savedAmountFromDatabase(goalId)).isEqualTo(2_000_000L);
        assertThat(statusFromDatabase(goalId)).isEqualTo("in_progress");
    }

    /**
     * GOAL-03 chế độ {@code create_transaction = true} — chuyển tiền THẬT từ ví nguồn sang ví mục
     * tiêu bằng một giao dịch {@code transfer}, cả hai số dư đổi đúng chiều.
     */
    @Test
    void addContribution_createTransactionTrue_createsTransferTransaction() throws Exception {
        String token = registerAndGetAccessToken("muc.tieu.nap.that@example.com");
        String sourceWalletId = createWallet(token, "Ví Tiêu Dùng", 10_000_000);
        String goalWalletId = createWallet(token, "Ví Tiết Kiệm", 1_000_000);
        String goalId = createGoal(token, "Mua xe máy", 50_000_000, goalWalletId, null);

        Map<?, ?> data = addContribution(token, goalId, 3_000_000, sourceWalletId, true);

        // Giao dịch thật được tạo, đúng loại transfer.
        Map<?, ?> transaction = (Map<?, ?>) data.get("transaction");
        assertThat(transaction).isNotNull();
        assertThat(transaction.get("type")).isEqualTo("transfer");

        // Số dư hai ví đổi đúng chiều: ví nguồn trừ, ví mục tiêu cộng.
        assertThat(walletBalance(sourceWalletId)).isEqualTo(7_000_000L);
        assertThat(walletBalance(goalWalletId)).isEqualTo(4_000_000L);

        // goal_contributions.transaction_id phải khác NULL.
        UUID linkedTransactionId = jdbcTemplate.queryForObject(
                "SELECT transaction_id FROM goal_contributions WHERE goal_id = ?",
                UUID.class,
                UUID.fromString(goalId));
        assertThat(linkedTransactionId).isNotNull();

        // Tiến độ do trigger tính.
        assertThat(savedAmountFromDatabase(goalId)).isEqualTo(3_000_000L);
        assertThat(((Number) ((Map<?, ?>) data.get("goal")).get("saved_amount")).longValue())
                .isEqualTo(3_000_000L);
    }

    /**
     * GOAL-03 chế độ {@code create_transaction = false} — chỉ ghi nhận tiến độ, tiền nằm ngoài
     * ứng dụng nên KHÔNG ví nào được đụng tới và KHÔNG giao dịch nào được tạo.
     */
    @Test
    void addContribution_createTransactionFalse_noWalletChange() throws Exception {
        String token = registerAndGetAccessToken("muc.tieu.nap.ao@example.com");
        String goalWalletId = createWallet(token, "Ví Tiết Kiệm", 1_000_000);
        String goalId = createGoal(token, "Mua xe máy", 50_000_000, goalWalletId, null);

        long balanceBefore = walletBalance(goalWalletId);
        Map<?, ?> data = addContribution(token, goalId, 3_000_000, null, false);

        // Không giao dịch nào được tạo, kể cả khi mục tiêu CÓ gắn ví — chế độ đọc từ request,
        // không suy đoán từ wallet_id (D-49).
        assertThat(liveTransactionCount()).isZero();
        assertThat(data.get("transaction")).isNull();
        assertThat(walletBalance(goalWalletId)).isEqualTo(balanceBefore);

        // transaction_id phải là NULL trong bảng con.
        UUID linkedTransactionId = jdbcTemplate.queryForObject(
                "SELECT transaction_id FROM goal_contributions WHERE goal_id = ?",
                UUID.class,
                UUID.fromString(goalId));
        assertThat(linkedTransactionId).isNull();

        // Nhưng tiến độ vẫn tăng đúng qua trigger.
        assertThat(savedAmountFromDatabase(goalId)).isEqualTo(3_000_000L);
    }

    /**
     * GOAL-03 — muốn chuyển tiền thật nhưng mục tiêu chưa gắn ví đích thì không có chỗ nào để
     * chuyển tới: 400 {@code GOAL_WALLET_REQUIRED} (api/09 mục B3).
     */
    @Test
    void addContribution_createTransactionTrueWithoutGoalWallet_returns400() throws Exception {
        String token = registerAndGetAccessToken("muc.tieu.khong.vi@example.com");
        String sourceWalletId = createWallet(token, "Ví Tiêu Dùng", 10_000_000);
        String goalId = createGoal(token, "Mua xe máy", 50_000_000, null, null);

        Map<String, Object> body = new HashMap<>();
        body.put("amount", 3_000_000);
        body.put("source_wallet_id", sourceWalletId);
        body.put("create_transaction", true);

        mockMvc.perform(post("/goals/" + goalId + "/contributions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("GOAL_WALLET_REQUIRED"));

        // Không có gì được ghi lại: không giao dịch, không bản ghi nạp, ví nguyên vẹn.
        assertThat(liveTransactionCount()).isZero();
        assertThat(savedAmountFromDatabase(goalId)).isZero();
        assertThat(walletBalance(sourceWalletId)).isEqualTo(10_000_000L);
    }

    /**
     * GOAL-04, mục ưu tiên số 2 của D-58 — BẰNG CHỨNG backend không tự ghi
     * {@code saved_amount}/{@code status}.
     *
     * <p>Nạp đủ target thì trigger tự đặt {@code completed}. Rút lại MỘT lần nạp khiến
     * {@code saved_amount < target_amount} thì trigger tự MỞ LẠI về {@code in_progress} — bước dễ
     * quên nhất, và là thứ backend sẽ làm sai nếu tự quản hai cột đó.
     */
    @Test
    void cancelContribution_reopensCompletedToInProgress() throws Exception {
        String token = registerAndGetAccessToken("muc.tieu.rut.lai@example.com");
        String sourceWalletId = createWallet(token, "Ví Tiêu Dùng", 10_000_000);
        String goalWalletId = createWallet(token, "Ví Tiết Kiệm", 0);
        String goalId = createGoal(token, "Mua điện thoại", 5_000_000, goalWalletId, null);

        // Hai lần nạp 2.500.000 -> vừa đủ target -> trigger tự đặt completed.
        Map<?, ?> first = addContribution(token, goalId, 2_500_000, sourceWalletId, true);
        Map<?, ?> second = addContribution(token, goalId, 2_500_000, sourceWalletId, true);

        assertThat(savedAmountFromDatabase(goalId)).isEqualTo(5_000_000L);
        assertThat(statusFromDatabase(goalId)).isEqualTo("completed");
        // Response của chính lần nạp thứ hai đã phải phản ánh giá trị SAU trigger — nếu đọc lại
        // bằng query trả entity thì Hibernate identity map sẽ trả về giá trị cũ.
        assertThat(((Map<?, ?>) second.get("goal")).get("status")).isEqualTo("completed");
        assertThat(((Number) ((Map<?, ?>) second.get("goal")).get("saved_amount")).longValue())
                .isEqualTo(5_000_000L);

        String contributionId =
                (String) ((Map<?, ?>) second.get("contribution")).get("id");

        mockMvc.perform(delete("/goals/" + goalId + "/contributions/" + contributionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Trigger tự trừ lại VÀ tự mở lại trạng thái.
        assertThat(savedAmountFromDatabase(goalId)).isEqualTo(2_500_000L);
        assertThat(statusFromDatabase(goalId)).isEqualTo("in_progress");

        // Giao dịch kèm theo được hoàn tác đúng: ví nguồn nhận lại 2.500.000 vừa rút ra
        // (10.000.000 - 2.500.000 - 2.500.000 + 2.500.000 = 7.500.000).
        assertThat(walletBalance(sourceWalletId)).isEqualTo(7_500_000L);
        assertThat(walletBalance(goalWalletId)).isEqualTo(2_500_000L);
        // Xoá MỀM để giữ audit trail — lần nạp đầu vẫn còn giao dịch sống.
        assertThat(liveTransactionCount()).isEqualTo(1);
        assertThat(first.get("transaction")).isNotNull();
    }
}

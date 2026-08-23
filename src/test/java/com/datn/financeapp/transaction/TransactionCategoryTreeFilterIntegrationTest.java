package com.datn.financeapp.transaction;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.util.HashMap;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * TXN-08 — chứng minh lọc {@code category_id} theo danh mục CHA cộng gộp cả giao dịch gán vào
 * danh mục CON (fn_category_tree), và CLAUDE.md §2 — {@code summary} loại trừ hoàn toàn giao
 * dịch {@code type = 'transfer'}. Đây là hai lỗi nghiệp vụ khó phát hiện nhất của toàn hệ thống:
 * sai thì báo cáo vẫn "trông hợp lý" nên không ai nghi ngờ.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionCategoryTreeFilterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1jYXQtdHJlZS1maWx0ZXItdGVzdA==");
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
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.update("DELETE FROM transactions");
        refreshTokenRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM wallets");
        jdbcTemplate.update("DELETE FROM categories WHERE user_id IS NOT NULL");
        userRepository.deleteAll();
    }

    private String registerAndGetAccessToken(String email) throws Exception {
        Map<String, Object> body = Map.of(
                "email", email,
                "password", "matkhau123",
                "full_name", "Người Kiểm Thử");
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

    private String firstWalletId(String token) throws Exception {
        String response = mockMvc.perform(get("/wallets").header("Authorization", "Bearer " + token))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Map<?, ?> parsed = objectMapper.readValue(response, Map.class);
        var wallets = (java.util.List<?>) parsed.get("data");
        return (String) ((Map<?, ?>) wallets.get(0)).get("id");
    }

    private String createWallet(String token, String name, long initialBalance) throws Exception {
        Map<String, Object> body = Map.of("name", name, "type", "cash", "initial_balance", initialBalance);
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

    private String findIconId(String code) {
        return jdbcTemplate.queryForObject("SELECT id FROM icons WHERE code = ?", String.class, code);
    }

    private String findCategoryGroupId(String name) {
        return jdbcTemplate.queryForObject("SELECT id FROM category_groups WHERE name = ?", String.class, name);
    }

    private String createRootCategory(String token, String name, String type) throws Exception {
        Map<String, Object> body = Map.of(
                "name", name,
                "type", type,
                "icon_id", findIconId("khac"),
                "color", "#3d6b7d",
                "category_group_id", findCategoryGroupId("Khác"));
        String response = mockMvc.perform(post("/categories")
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

    private String createChildCategory(String token, String name, String type, String parentId) throws Exception {
        Map<String, Object> body = Map.of(
                "name", name,
                "type", type,
                "icon_id", findIconId("khac"),
                "color", "#3d6b7d",
                "parent_category_id", parentId);
        String response = mockMvc.perform(post("/categories")
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

    private void createExpenseTransaction(String token, String walletId, String categoryId, long amount)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "expense");
        body.put("amount", amount);
        body.put("wallet_id", walletId);
        body.put("category_id", categoryId);
        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    @Test
    void filterByParentCategory_includesChildCategoryTransactions_excludesTransferWhenRequested() throws Exception {
        String token = registerAndGetAccessToken("cong.gop.danh.muc@example.com");
        String walletA = firstWalletId(token);
        String walletB = createWallet(token, "Ví B", 500_000);

        String parentId = createRootCategory(token, "Ăn uống test", "expense");
        String coffeeId = createChildCategory(token, "Cà phê test", "expense", parentId);
        String restaurantId = createChildCategory(token, "Nhà hàng test", "expense", parentId);

        // 1 giao dịch gán thẳng vào cha, 2 giao dịch gán vào 2 con khác nhau.
        createExpenseTransaction(token, walletA, parentId, 50_000);
        createExpenseTransaction(token, walletA, coffeeId, 45_000);
        createExpenseTransaction(token, walletA, restaurantId, 120_000);

        // 1 giao dịch transfer không liên quan danh mục — không được lọt vào khi include_transfers=false.
        Map<String, Object> transferBody = new HashMap<>();
        transferBody.put("type", "transfer");
        transferBody.put("amount", 200_000);
        transferBody.put("wallet_id", walletA);
        transferBody.put("destination_wallet_id", walletB);
        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferBody)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .param("category_id", parentId)
                        .param("include_transfers", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.pagination.total_items").value(3));
    }

    @Test
    void summary_excludesTransferFromIncomeAndExpenseTotals() throws Exception {
        String token = registerAndGetAccessToken("summary.loai.transfer@example.com");
        String walletA = firstWalletId(token);
        String walletB = createWallet(token, "Ví B Summary", 500_000);

        String incomeCategoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE user_id IS NULL AND type = 'income' LIMIT 1", String.class);
        String expenseCategoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE user_id IS NULL AND type = 'expense' LIMIT 1", String.class);

        Map<String, Object> incomeBody = new HashMap<>();
        incomeBody.put("type", "income");
        incomeBody.put("amount", 1_000_000);
        incomeBody.put("wallet_id", walletA);
        incomeBody.put("category_id", incomeCategoryId);
        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(incomeBody)))
                .andExpect(status().isCreated());

        createExpenseTransaction(token, walletA, expenseCategoryId, 300_000);

        // Giao dịch transfer với số tiền LỚN — nếu lọt vào summary sẽ làm sai lệch rõ ràng.
        Map<String, Object> transferBody = new HashMap<>();
        transferBody.put("type", "transfer");
        transferBody.put("amount", 5_000_000);
        transferBody.put("wallet_id", walletA);
        transferBody.put("destination_wallet_id", walletB);
        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferBody)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/transactions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.total_income").value(1_000_000))
                .andExpect(jsonPath("$.summary.total_expense").value(300_000))
                .andExpect(jsonPath("$.summary.difference").value(700_000));
    }
}

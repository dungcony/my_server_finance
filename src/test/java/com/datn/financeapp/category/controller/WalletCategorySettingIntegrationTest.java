package com.datn.financeapp.category.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datn.financeapp.TestAuthSupport;
import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.auth.repository.OtpRepository;
import com.datn.financeapp.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.common.ratelimit.RateLimitFilter;
import com.datn.financeapp.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
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
 * Bật/tắt danh mục theo từng ví — {@code GET|PATCH /wallets/{walletId}/categories}
 * (api/03-DANH-MUC.md mục 9, bảng {@code wallet_category_settings} ở V17).
 *
 * <p>Hai quy ước dễ hiểu nhầm được khoá lại ở đây:
 *
 * <ol>
 *   <li><b>Vắng dòng trong bảng = đang bật.</b> Bảng chỉ ghi các dòng lệch mặc định, nên ví vừa
 *       tạo chưa có dòng nào vẫn phải trả {@code is_enabled = true} cho mọi danh mục.
 *   <li><b>Tắt cha kéo con tắt theo.</b> Con hiện dưới cha trong cùng cây; để con bật lơ lửng
 *       dưới cha đã tắt là trạng thái vô nghĩa với người dùng.
 * </ol>
 *
 * <p>Cộng thêm ranh giới quyền D-27: ví của người khác trả <b>404</b>, không phải 403 — không lộ
 * việc ví đó có tồn tại hay không.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import({TestRedisConfig.class, TestAuthSupport.class})
class WalletCategorySettingIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci13YWxsZXQtY2F0LXNldHRpbmctMzJi");
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

    @Autowired
    private TestAuthSupport authSupport;

    @Autowired
    private OtpRepository otpRepository;

    @BeforeEach
    void cleanTables() {
        // OTP nằm ở Redis, không bị Testcontainers PostgreSQL dọn hộ.
        otpRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM wallet_category_settings");
        jdbcTemplate.update("DELETE FROM transactions");
        refreshTokenRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM wallets");
        jdbcTemplate.update("DELETE FROM categories WHERE user_id IS NOT NULL");
        userRepository.deleteAll();
    }

    private String registerAndGetAccessToken(String email) throws Exception {
        return authSupport.registerAndGetAccessToken(email, "Người Kiểm Thử Danh Mục Theo Ví");
    }

    private String createWallet(String token, String name) throws Exception {
        Map<String, Object> body = Map.of(
                "name", name,
                "type", "cash",
                "initial_balance", 0);
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

    /** Tạo danh mục cha; trả id để test gắn con vào hoặc bật/tắt. */
    private String createRootCategory(String token, String name) throws Exception {
        Map<String, Object> body = Map.of(
                "name", name,
                "type", "expense",
                "icon_id", findIconId("khac"),
                "color", "#3d6b7d",
                "category_group_id", findCategoryGroupId("Khác"));
        return performCreateCategory(token, body);
    }

    private String createChildCategory(String token, String name, String parentId) throws Exception {
        Map<String, Object> body = Map.of(
                "name", name,
                "type", "expense",
                "icon_id", findIconId("khac"),
                "color", "#3d6b7d",
                "parent_category_id", parentId);
        return performCreateCategory(token, body);
    }

    private String performCreateCategory(String token, Map<String, Object> body) throws Exception {
        String response = mockMvc.perform(post("/categories")
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

    private void toggleCategory(String token, String walletId, String categoryId, boolean enabled)
            throws Exception {
        mockMvc.perform(patch("/wallets/" + walletId + "/categories/" + categoryId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("is_enabled", enabled))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Ví mới chưa có dòng cài đặt nào thì mọi danh mục vẫn đang bật")
    void walletWithoutSettingRows_returnsAllCategoriesEnabled() throws Exception {
        String token = registerAndGetAccessToken("vi.moi.mac.dinh@example.com");
        String walletId = createWallet(token, "Ví Mặc Định");

        mockMvc.perform(get("/wallets/" + walletId + "/categories")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isNotEmpty())
                // Danh mục hệ thống của seed V5 đều chưa có dòng lệch mặc định.
                .andExpect(jsonPath("$.data[0].is_enabled").value(true));
    }

    @Test
    @DisplayName("Tắt một danh mục rồi đọc lại thì danh mục đó có is_enabled = false")
    void disableCategory_thenReadBack_returnsDisabled() throws Exception {
        String token = registerAndGetAccessToken("tat.mot.danh.muc@example.com");
        String walletId = createWallet(token, "Ví Tắt Nhóm");
        String categoryId = createRootCategory(token, "Nhóm Sẽ Tắt");

        toggleCategory(token, walletId, categoryId, false);

        mockMvc.perform(get("/wallets/" + walletId + "/categories")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '" + categoryId + "')].is_enabled").value(false));
    }

    @Test
    @DisplayName("Tắt danh mục cha thì các danh mục con tắt theo")
    void disableParentCategory_alsoDisablesChildren() throws Exception {
        String token = registerAndGetAccessToken("tat.cha.keo.con@example.com");
        String walletId = createWallet(token, "Ví Cây Hai Cấp");
        String parentId = createRootCategory(token, "Nhóm Cha");
        String childId = createChildCategory(token, "Nhóm Con", parentId);

        toggleCategory(token, walletId, parentId, false);

        mockMvc.perform(get("/wallets/" + walletId + "/categories")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '" + parentId + "')].is_enabled").value(false))
                .andExpect(jsonPath(
                                "$.data[?(@.id == '" + parentId + "')].children[?(@.id == '" + childId
                                        + "')].is_enabled")
                        .value(false));
    }

    @Test
    @DisplayName("Bật lại danh mục đã tắt thì trở về trạng thái bật")
    void reEnableDisabledCategory_returnsEnabledAgain() throws Exception {
        String token = registerAndGetAccessToken("bat.lai.danh.muc@example.com");
        String walletId = createWallet(token, "Ví Bật Lại");
        String categoryId = createRootCategory(token, "Nhóm Bật Lại");

        toggleCategory(token, walletId, categoryId, false);
        toggleCategory(token, walletId, categoryId, true);

        mockMvc.perform(get("/wallets/" + walletId + "/categories")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '" + categoryId + "')].is_enabled").value(true));
    }

    @Test
    @DisplayName("Đọc danh mục của ví người khác trả 404, không phải 403")
    void readCategoriesOfAnotherUsersWallet_returns404NotFound() throws Exception {
        String tokenA = registerAndGetAccessToken("nguoi.a.vi.cat@example.com");
        String tokenB = registerAndGetAccessToken("nguoi.b.vi.cat@example.com");
        String walletOfB = createWallet(tokenB, "Ví Riêng Của B");

        mockMvc.perform(get("/wallets/" + walletOfB + "/categories")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("Tắt danh mục trên ví người khác trả 404, không phải 403")
    void toggleCategoryOnAnotherUsersWallet_returns404NotFound() throws Exception {
        String tokenA = registerAndGetAccessToken("nguoi.a.vi.toggle@example.com");
        String tokenB = registerAndGetAccessToken("nguoi.b.vi.toggle@example.com");
        String walletOfB = createWallet(tokenB, "Ví B Toggle");
        String categoryOfB = createRootCategory(tokenB, "Nhóm Của B");

        mockMvc.perform(patch("/wallets/" + walletOfB + "/categories/" + categoryOfB)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("is_enabled", false))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("Tắt danh mục không ảnh hưởng giao dịch cũ đã gán vào nó")
    void disablingCategory_doesNotAffectExistingTransactions() throws Exception {
        String token = registerAndGetAccessToken("tat.khong.anh.huong@example.com");
        String walletId = createWallet(token, "Ví Có Giao Dịch");
        String categoryId = createRootCategory(token, "Nhóm Có Giao Dịch");

        Map<String, Object> txBody = Map.of(
                "wallet_id", walletId,
                "category_id", categoryId,
                "type", "expense",
                "amount", 50000,
                "date", "2026-09-13");
        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(txBody)))
                .andExpect(status().isCreated());

        toggleCategory(token, walletId, categoryId, false);

        // Giao dịch vẫn còn nguyên: tắt chỉ ẩn danh mục khỏi màn chọn, không đụng dữ liệu cũ.
        Long remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE category_id = ?::uuid AND NOT is_deleted",
                Long.class, categoryId);
        org.junit.jupiter.api.Assertions.assertEquals(1L, remaining);
    }
}

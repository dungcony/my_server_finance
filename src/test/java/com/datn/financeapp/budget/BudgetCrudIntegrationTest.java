package com.datn.financeapp.budget;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datn.financeapp.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.auth.repository.UserRepository;
import com.datn.financeapp.common.ratelimit.RateLimitFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
 * CRUD ngân sách (BUDGET-01..05, api/05-NGAN-SACH.md). Bốn luồng rủi ro theo D-58: chặn trùng ở
 * tầng CSDL, chặn sửa danh mục/kỳ, xoá mềm là tắt {@code is_active} chứ không xoá bản ghi, và gợi
 * ý hạn mức khi chưa đủ lịch sử.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BudgetCrudIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1idWRnZXQtY3J1ZC10ZXN0LTMyYg==");
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
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.update("DELETE FROM budgets");
        jdbcTemplate.update("DELETE FROM transactions");
        refreshTokenRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM wallets");
        jdbcTemplate.update("DELETE FROM categories WHERE user_id IS NOT NULL");
        userRepository.deleteAll();
    }

    // ---------------------------------------------------------------- helpers

    private String registerAndGetAccessToken(String email) throws Exception {
        Map<String, Object> body = Map.of(
                "email", email,
                "password", "matkhau123",
                "username", "Người Kiểm Thử Ngân Sách");
        String response = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Map<?, ?> data = (Map<?, ?>) objectMapper.readValue(response, Map.class).get("data");
        return (String) data.get("access_token");
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

    private String createBudget(String token, String categoryId, long limitAmount, String periodType)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("category_id", categoryId);
        body.put("limit_amount", limitAmount);
        body.put("period_type", periodType);
        String response = mockMvc.perform(post("/budgets")
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

    // ------------------------------------------------------------------ tests

    /**
     * BUDGET-03 — ràng buộc {@code ex_bud_no_overlap} (EXCLUDE gist, V3) chặn ở tầng CSDL; service
     * chỉ bắt {@code DataIntegrityViolationException} và dịch sang mã nghiệp vụ 409. Không có
     * SELECT kiểm tra trước nên không có khe hở race condition.
     */
    @Test
    void createBudget_duplicatePeriod_returns409() throws Exception {
        String token = registerAndGetAccessToken("ngan.sach.trung@example.com");
        String categoryId = createRootCategory(token, "Ăn uống test", "expense");

        createBudget(token, categoryId, 3_000_000, "month");

        Map<String, Object> duplicate = new HashMap<>();
        duplicate.put("category_id", categoryId);
        duplicate.put("limit_amount", 5_000_000);
        duplicate.put("period_type", "month");

        mockMvc.perform(post("/budgets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BUDGET_ALREADY_EXISTS"));
    }

    /** api/05 mục 5 — đổi danh mục thực chất là một ngân sách khác, phải xoá và tạo lại (T-04-06). */
    @Test
    void updateBudget_changeCategoryId_returns400CategoryNotEditable() throws Exception {
        String token = registerAndGetAccessToken("ngan.sach.doi.danh.muc@example.com");
        String categoryId = createRootCategory(token, "Ăn uống test", "expense");
        String otherCategoryId = createRootCategory(token, "Đi lại test", "expense");
        String budgetId = createBudget(token, categoryId, 3_000_000, "month");

        mockMvc.perform(patch("/budgets/" + budgetId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("category_id", otherCategoryId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CATEGORY_NOT_EDITABLE"));

        // Đổi period_type cũng bị chặn bằng đúng mã lỗi đó.
        mockMvc.perform(patch("/budgets/" + budgetId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("period_type", "week"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CATEGORY_NOT_EDITABLE"));
    }

    /**
     * api/05 mục 5 "Xoá là xoá mềm". Bảng {@code budgets} KHÔNG có cột {@code is_deleted} (schema
     * V3 thật) — xoá mềm nghĩa là tắt {@code is_active}, bản ghi phải CÒN NGUYÊN trong CSDL để
     * giữ lịch sử và để BUDGET-05 còn dùng kỳ cũ làm cơ sở gợi ý.
     */
    @Test
    void deleteBudget_setsIsActiveFalse_notPhysicalDelete() throws Exception {
        String token = registerAndGetAccessToken("ngan.sach.xoa.mem@example.com");
        String categoryId = createRootCategory(token, "Giải trí test", "expense");
        String budgetId = createBudget(token, categoryId, 2_000_000, "month");

        mockMvc.perform(delete("/budgets/" + budgetId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM budgets WHERE id = ?", Integer.class, UUID.fromString(budgetId));
        Assertions.assertEquals(1, rowCount, "Xoá mềm KHÔNG được xoá bản ghi khỏi CSDL");

        Boolean isActive = jdbcTemplate.queryForObject(
                "SELECT is_active FROM budgets WHERE id = ?", Boolean.class, UUID.fromString(budgetId));
        Assertions.assertEquals(Boolean.FALSE, isActive, "Xoá mềm phải tắt is_active");

        // Đã tắt thì biến mất khỏi danh sách đang hiệu lực...
        mockMvc.perform(get("/budgets").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        // ...và thôi chiếm chỗ trong ex_bud_no_overlap, nên tạo lại cùng danh mục/kỳ phải được.
        createBudget(token, categoryId, 2_500_000, "month");
    }

    /**
     * BUDGET-05, api/05 mục 6 — chưa có kỳ ngân sách nào ĐÃ KẾT THÚC thì trả {@code null} tường
     * minh kèm giải thích, để app ẩn khối gợi ý thay vì hiện con số bịa từ mẫu quá nhỏ.
     */
    @Test
    void getSuggestion_lessThanOneMonthData_returnsNullSuggestedLimit() throws Exception {
        String token = registerAndGetAccessToken("ngan.sach.goi.y@example.com");
        String categoryId = createRootCategory(token, "Mua sắm test", "expense");
        createBudget(token, categoryId, 1_000_000, "month");

        mockMvc.perform(get("/budgets/suggestion")
                        .param("category_id", categoryId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.suggested_limit").doesNotExist())
                .andExpect(jsonPath("$.data.basis.months_with_data").value(0))
                .andExpect(jsonPath("$.data.explanation").isNotEmpty());
    }
}

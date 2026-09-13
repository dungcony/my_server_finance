package com.datn.financeapp.category;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datn.financeapp.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.user.repository.UserRepository;
import com.datn.financeapp.common.ratelimit.RateLimitFilter;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * D-28 mục 3 áp dụng cho danh mục: user A tạo danh mục riêng, user B gọi GET/PATCH/DELETE danh
 * mục đó phải nhận 404 (không phải 403) — T-02-09, {@code CategoryRepository.findByIdAndVisibleToUser}
 * không có bản ghi thoả điều kiện user_id = B. Đồng thời cả hai user đều thấy được danh mục hệ
 * thống (GET trả 200) để xác nhận không chặn nhầm (api/03-DANH-MUC.md mục 9).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CategoryAccessControlIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1jYXRlZ29yeS1hY2Nlc3MtdGVzdC0zMmI=");
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
                "username", "Người Kiểm Thử Quyền Danh Mục");
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

    private String findIconId(String code) {
        return jdbcTemplate.queryForObject("SELECT id FROM icons WHERE code = ?", String.class, code);
    }

    private String findCategoryGroupId(String name) {
        return jdbcTemplate.queryForObject("SELECT id FROM category_groups WHERE name = ?", String.class, name);
    }

    private String createCategory(String token, String name) throws Exception {
        Map<String, Object> body = Map.of(
                "name", name,
                "type", "expense",
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
        Map<?, ?> parsed = objectMapper.readValue(response, Map.class);
        Map<?, ?> data = (Map<?, ?>) parsed.get("data");
        return (String) data.get("id");
    }

    @Test
    void userA_getCategoryOfUserB_returns404NotFound() throws Exception {
        String tokenA = registerAndGetAccessToken("nguoi.a.cat.get@example.com");
        String tokenB = registerAndGetAccessToken("nguoi.b.cat.get@example.com");
        String categoryOfB = createCategory(tokenB, "Danh Mục Riêng Của B");

        mockMvc.perform(get("/categories/" + categoryOfB).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void userA_patchCategoryOfUserB_returns404NotFound() throws Exception {
        String tokenA = registerAndGetAccessToken("nguoi.a.cat.patch@example.com");
        String tokenB = registerAndGetAccessToken("nguoi.b.cat.patch@example.com");
        String categoryOfB = createCategory(tokenB, "Danh Mục B Patch");

        Map<String, Object> body = Map.of("name", "Tên Mới");

        mockMvc.perform(patch("/categories/" + categoryOfB)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void userA_deleteCategoryOfUserB_returns404NotFound() throws Exception {
        String tokenA = registerAndGetAccessToken("nguoi.a.cat.delete@example.com");
        String tokenB = registerAndGetAccessToken("nguoi.b.cat.delete@example.com");
        String categoryOfB = createCategory(tokenB, "Danh Mục B Delete");

        mockMvc.perform(delete("/categories/" + categoryOfB).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void userA_createChildUnderUserBPrivateParent_returns404NotFound() throws Exception {
        String tokenA = registerAndGetAccessToken("nguoi.a.cat.create.child@example.com");
        String tokenB = registerAndGetAccessToken("nguoi.b.cat.create.child@example.com");
        String parentOfB = createCategory(tokenB, "Danh Mục Cha Riêng Của B");

        Map<String, Object> body = Map.of(
                "name", "Con Của A Dưới Cha B",
                "type", "expense",
                "icon_id", findIconId("khac"),
                "color", "#3d6b7d",
                "parent_category_id", parentOfB);

        mockMvc.perform(post("/categories")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void userA_patchOwnCategoryParentToUserBPrivateCategory_returns404NotFound() throws Exception {
        String tokenA = registerAndGetAccessToken("nguoi.a.cat.patch.parent@example.com");
        String tokenB = registerAndGetAccessToken("nguoi.b.cat.patch.parent@example.com");
        String parentOfB = createCategory(tokenB, "Danh Mục Cha Riêng Của B Patch");
        String categoryOfA = createCategory(tokenA, "Danh Mục Của A Patch Parent");

        Map<String, Object> body = Map.of("parent_category_id", parentOfB);

        mockMvc.perform(patch("/categories/" + categoryOfA)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void bothUsers_seeSystemCategory_returns200() throws Exception {
        String tokenA = registerAndGetAccessToken("nguoi.a.cat.system@example.com");
        String tokenB = registerAndGetAccessToken("nguoi.b.cat.system@example.com");
        String systemCategoryId =
                jdbcTemplate.queryForObject("SELECT id FROM categories WHERE user_id IS NULL AND name = 'Ăn uống' LIMIT 1", String.class);

        mockMvc.perform(get("/categories/" + systemCategoryId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.is_system").value(true));

        mockMvc.perform(get("/categories/" + systemCategoryId).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.is_system").value(true));
    }
}

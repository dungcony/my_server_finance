package com.datn.financeapp.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datn.financeapp.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.auth.repository.UserRepository;
import com.datn.financeapp.category.repository.CategoryRepository;
import com.datn.financeapp.common.ratelimit.RateLimitFilter;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * D-28 mục 4: cây danh mục hai cấp — chặn tạo cấp ba, chặn con khác type với cha, chặn xoá còn
 * con/đang được dùng, kế thừa category_group_id, cây trả về gồm cả danh mục hệ thống lẫn của
 * user. Dùng Testcontainers PostgreSQL thật theo D-23, seed hệ thống có sẵn từ V5/V8.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CategoryTreeIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1jYXRlZ29yeS10cmVlLXRlc3QtMzJi");
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
    private CategoryRepository categoryRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.update("DELETE FROM transactions");
        refreshTokenRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM wallets");
        // Chỉ xoá danh mục của user (user_id NOT NULL) — giữ nguyên danh mục hệ thống seed từ V5/V8.
        jdbcTemplate.update("DELETE FROM categories WHERE user_id IS NOT NULL");
        userRepository.deleteAll();
    }

    private String registerAndGetAccessToken(String email) throws Exception {
        Map<String, Object> body = Map.of(
                "email", email,
                "password", "matkhau123",
                "username", "Người Kiểm Thử Danh Mục");
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

    private Map<?, ?> createRootCategory(String token, String name, String type) throws Exception {
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
        Map<?, ?> parsed = objectMapper.readValue(response, Map.class);
        return (Map<?, ?>) parsed.get("data");
    }

    // ---------- Behavior Task 1 ----------

    @Test
    void createChildOfChild_returns400MaxDepthExceeded() throws Exception {
        String token = registerAndGetAccessToken("depth@example.com");
        Map<?, ?> root = createRootCategory(token, "Gốc Độ Sâu", "expense");
        Map<String, Object> childBody = Map.of(
                "name", "Con Cấp 2",
                "type", "expense",
                "icon_id", findIconId("khac"),
                "color", "#3d6b7d",
                "parent_category_id", root.get("id"));
        String childResponse = mockMvc.perform(post("/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(childBody)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Map<?, ?> childData = (Map<?, ?>) objectMapper.readValue(childResponse, Map.class).get("data");

        Map<String, Object> grandChildBody = Map.of(
                "name", "Cháu Cấp 3",
                "type", "expense",
                "icon_id", findIconId("khac"),
                "color", "#3d6b7d",
                "parent_category_id", childData.get("id"));

        mockMvc.perform(post("/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(grandChildBody)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MAX_DEPTH_EXCEEDED"));
    }

    @Test
    void createChildWithDifferentType_returns400TypeMismatch() throws Exception {
        String token = registerAndGetAccessToken("typemismatch@example.com");
        Map<?, ?> root = createRootCategory(token, "Gốc Chi", "expense");

        Map<String, Object> childBody = Map.of(
                "name", "Con Loại Khác",
                "type", "income",
                "icon_id", findIconId("khac"),
                "color", "#3d6b7d",
                "parent_category_id", root.get("id"));

        mockMvc.perform(post("/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(childBody)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("TYPE_MISMATCH_WITH_PARENT"));
    }

    @Test
    void createRootWithoutCategoryGroup_returns400CategoryGroupRequired() throws Exception {
        String token = registerAndGetAccessToken("nogroup@example.com");
        Map<String, Object> body = Map.of(
                "name", "Danh Mục Không Nhóm",
                "type", "expense",
                "icon_id", findIconId("khac"),
                "color", "#3d6b7d");

        mockMvc.perform(post("/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CATEGORY_GROUP_REQUIRED"));
    }

    @Test
    void deleteCategoryWithChildren_returns409ChildCategoriesExist() throws Exception {
        String token = registerAndGetAccessToken("haschildren@example.com");
        Map<?, ?> root = createRootCategory(token, "Gốc Có Con", "expense");

        Map<String, Object> childBody = Map.of(
                "name", "Con Của Gốc",
                "type", "expense",
                "icon_id", findIconId("khac"),
                "color", "#3d6b7d",
                "parent_category_id", root.get("id"));
        mockMvc.perform(post("/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(childBody)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/categories/" + root.get("id")).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CHILD_CATEGORIES_EXIST"));
    }

    @Test
    void patchSystemCategory_returns403SystemCategoryNotEditable() throws Exception {
        String token = registerAndGetAccessToken("editsystem@example.com");
        String systemCategoryId =
                jdbcTemplate.queryForObject("SELECT id FROM categories WHERE user_id IS NULL AND name = 'Ăn uống' LIMIT 1", String.class);

        Map<String, Object> body = Map.of("name", "Ăn uống sửa");

        mockMvc.perform(patch("/categories/" + systemCategoryId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SYSTEM_CATEGORY_NOT_EDITABLE"));
    }

    @Test
    void deleteSystemCategory_returns403SystemCategoryNotDeletable() throws Exception {
        String token = registerAndGetAccessToken("deletesystem@example.com");
        String systemCategoryId =
                jdbcTemplate.queryForObject("SELECT id FROM categories WHERE user_id IS NULL AND name = 'Ăn uống' LIMIT 1", String.class);

        mockMvc.perform(delete("/categories/" + systemCategoryId).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SYSTEM_CATEGORY_NOT_DELETABLE"));
    }

    // ---------- Behavior bổ sung Task 2 ----------

    @Test
    void getCategoriesAsTree_returnsSystemAndUserCategoriesInTwoLevelTree() throws Exception {
        String token = registerAndGetAccessToken("tree@example.com");
        createRootCategory(token, "Danh Mục Riêng Của Tôi", "expense");

        String response = mockMvc.perform(get("/categories")
                        .header("Authorization", "Bearer " + token)
                        .param("type", "expense")
                        .param("as_tree", "true"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<?, ?> parsed = objectMapper.readValue(response, Map.class);
        List<?> data = (List<?>) parsed.get("data");

        boolean hasSystemCategory = data.stream()
                .anyMatch(item -> Boolean.TRUE.equals(((Map<?, ?>) item).get("is_system"))
                        && "Ăn uống".equals(((Map<?, ?>) item).get("name")));
        boolean hasUserCategory = data.stream()
                .anyMatch(item -> Boolean.FALSE.equals(((Map<?, ?>) item).get("is_system"))
                        && "Danh Mục Riêng Của Tôi".equals(((Map<?, ?>) item).get("name")));

        assertThat(hasSystemCategory).isTrue();
        assertThat(hasUserCategory).isTrue();

        Map<?, ?> anUongNode = data.stream()
                .map(item -> (Map<?, ?>) item)
                .filter(item -> "Ăn uống".equals(item.get("name")))
                .findFirst()
                .orElseThrow();
        List<?> children = (List<?>) anUongNode.get("children");
        assertThat(children).isNotEmpty();
    }

    @Test
    void createChildCategory_inheritsParentCategoryGroupId() throws Exception {
        String token = registerAndGetAccessToken("inherit@example.com");
        Map<?, ?> root = createRootCategory(token, "Gốc Kế Thừa Nhóm", "expense");
        Map<?, ?> rootGroup = (Map<?, ?>) root.get("category_group");
        assertThat(rootGroup).isNotNull();

        Map<String, Object> childBody = Map.of(
                "name", "Con Kế Thừa Nhóm",
                "type", "expense",
                "icon_id", findIconId("khac"),
                "color", "#3d6b7d",
                "parent_category_id", root.get("id"));

        String response = mockMvc.perform(post("/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(childBody)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<?, ?> childData = (Map<?, ?>) objectMapper.readValue(response, Map.class).get("data");
        Map<?, ?> childGroup = (Map<?, ?>) childData.get("category_group");

        assertThat(childGroup).isNotNull();
        assertThat(childGroup.get("id")).isEqualTo(rootGroup.get("id"));
    }
}

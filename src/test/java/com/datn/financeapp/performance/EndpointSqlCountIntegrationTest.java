package com.datn.financeapp.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.datn.financeapp.TestAuthSupport;
import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.auth.repository.OtpRepository;
import com.datn.financeapp.common.ratelimit.RateLimitFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
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
 * Đo số câu SQL của các endpoint thuộc ba module {@code user}, {@code auth}, {@code group} để thấy
 * endpoint nào đang N+1 mà không phải gọi tay từng API.
 *
 * <p>Cách phát hiện N+1: đo hai lần với lượng dữ liệu khác nhau (ít rồi nhiều thành viên, người dùng
 * và giao dịch nhóm). Endpoint nào có số câu SQL <b>tăng theo dữ liệu</b> là nghi N+1. Số câu SQL cao
 * nhưng đứng yên thì chỉ là truy vấn nhiều mà không bị N+1.
 *
 * <p>Phạm vi: các endpoint đọc của {@code user} ({@code /users/me}, {@code /admin/user},
 * {@code /admin/role} — dùng tài khoản admin có sẵn từ migration), {@code group} (nhóm, thành viên,
 * giao dịch, báo cáo) và hai endpoint của {@code auth} nặng nhất về truy vấn là {@code login} và
 * {@code refresh} ({@code auth} không có endpoint GET).
 *
 * <p>Không phải test hành vi: lỗi HTTP chỉ được ghi vào báo cáo chứ không làm đỏ test, để một
 * đường dẫn hay dữ liệu mẫu sai không che mất số liệu của các endpoint còn lại. Báo cáo ghi ra log
 * (mức WARN) và file {@code target/endpoint-sql-report.txt}. Chạy riêng:
 * {@code mvn test -Dtest=EndpointSqlCountIntegrationTest}
 */
@Slf4j
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import({TestRedisConfig.class, TestAuthSupport.class, SqlCountingConfig.class})
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
class EndpointSqlCountIntegrationTest {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Path REPORT_FILE = Path.of("target", "endpoint-sql-report.txt");
    private static final String ADMIN_EMAIL = "admin@financeapp.com";
    private static final String ADMIN_PASSWORD = "Admin@123";
    private static final String OWNER_EMAIL = "do.sql.owner@example.com";
    private static final String OWNER_PASSWORD = "matkhau123";

    private static final int FEW_MEMBERS = 2;
    private static final int MORE_MEMBERS = 4;
    private static final int FEW_TRANSACTIONS = 10;
    private static final int MORE_TRANSACTIONS = 40;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1lbmRwb2ludC1zcWwtY291bnQtMDQwNA==");
    }

    // gọi hàng trăm request liên tiếp nên phải tắt giới hạn tốc độ, giống các test tích hợp khác
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
    private OtpRepository otpRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestAuthSupport authSupport;

    // mã OTP nằm ở Redis, không bị Testcontainers PostgreSQL dọn hộ
    @BeforeEach
    void cleanOtp() {
        otpRepository.deleteAll();
    }

    @Test
    @DisplayName("Đo SQL các endpoint user/auth/group ở hai mức dữ liệu để lộ N+1")
    void sweepUserAuthGroupEndpoints_logsSqlCountAtTwoDataSizes() throws Exception {
        String ownerToken = authSupport.registerAndGetAccessToken(OWNER_EMAIL);
        String ownerId = currentUserId(ownerToken);
        String adminToken = loginOrNull(ADMIN_EMAIL, ADMIN_PASSWORD);
        String expenseCategoryId = systemCategoryId("expense");

        int memberCount = 0;
        List<String> firstMembers = registerMembers(memberCount, FEW_MEMBERS);
        memberCount += FEW_MEMBERS;
        String groupId = createGroup(ownerToken, firstMembers);
        int transactionCount = seedGroupTransactions(ownerToken, groupId, ownerId, expenseCategoryId, 0, FEW_TRANSACTIONS);

        List<Endpoint> endpoints = buildEndpoints(ownerToken, ownerId, adminToken, groupId);
        Map<String, Measurement> few = sweep(endpoints);

        List<String> moreMembers = registerMembers(memberCount, MORE_MEMBERS);
        addGroupMembers(ownerToken, groupId, moreMembers);
        seedGroupTransactions(ownerToken, groupId, ownerId, expenseCategoryId, transactionCount, MORE_TRANSACTIONS);
        Map<String, Measurement> many = sweep(endpoints);

        String report = buildReport(endpoints, few, many, adminToken != null);
        log.warn("\n{}", report);
        Files.createDirectories(REPORT_FILE.getParent());
        Files.writeString(REPORT_FILE, report);

        assertThat(many).isNotEmpty();
    }

    private List<Endpoint> buildEndpoints(String ownerToken, String ownerId, String adminToken, String groupId) {
        String group = "/groups/" + groupId;
        AtomicReference<String> refreshToken = new AtomicReference<>();
        List<Endpoint> endpoints = new ArrayList<>(List.of(
                // user
                Endpoint.of("GET /users/me", getAction("/users/me", ownerToken)),

                // auth: login và refresh là hai endpoint auth nặng truy vấn nhất
                Endpoint.of("POST /auth/login", loginAction(OWNER_EMAIL, OWNER_PASSWORD)),
                new Endpoint("POST /auth/refresh",
                        () -> refreshToken.set(loginAndGetRefreshToken(OWNER_EMAIL, OWNER_PASSWORD)),
                        () -> postJson("/auth/refresh", null, Map.of("refresh_token", refreshToken.get()))),

                // group
                Endpoint.of("GET /groups", getAction("/groups", ownerToken)),
                Endpoint.of("GET /groups/{id}", getAction(group, ownerToken)),
                Endpoint.of("GET /groups/{id}/pending-count", getAction(group + "/pending-count", ownerToken)),
                Endpoint.of("GET /groups/{id}/members", getAction(group + "/members", ownerToken)),
                Endpoint.of("GET /groups/{id}/summary", getAction(group + "/summary", ownerToken)),
                Endpoint.of("GET /groups/{id}/balances", getAction(group + "/balances", ownerToken)),
                Endpoint.of("GET /groups/{id}/transactions", getAction(group + "/transactions", ownerToken)),
                Endpoint.of("GET /groups/{id}/transactions/mine", getAction(group + "/transactions/mine", ownerToken)),
                Endpoint.of("GET /groups/{id}/transactions/pending", getAction(group + "/transactions/pending", ownerToken))));

        String firstTransactionId = firstGroupTransactionId(ownerToken, groupId);
        if (firstTransactionId != null)
            endpoints.add(Endpoint.of("GET /groups/{id}/transactions/{txnId}",
                    getAction(group + "/transactions/" + firstTransactionId, ownerToken)));

        // endpoint admin cần tài khoản admin có sẵn từ migration; đăng nhập không được thì bỏ qua
        if (adminToken != null) {
            endpoints.add(Endpoint.of("GET /admin/user/all", getAction("/admin/user/all", adminToken)));
            endpoints.add(Endpoint.of("GET /admin/user/{userId}", getAction("/admin/user/" + ownerId, adminToken)));
            endpoints.add(Endpoint.of("GET /admin/role/all", getAction("/admin/role/all", adminToken)));
            endpoints.add(Endpoint.of("GET /admin/role/ROLE_USER/permissions",
                    getAction("/admin/role/ROLE_USER/permissions", adminToken)));
        }
        return endpoints;
    }

    // mỗi endpoint chạy một lần để khởi động nguội rồi mới đo lần thứ hai
    private Map<String, Measurement> sweep(List<Endpoint> endpoints) {
        Map<String, Measurement> results = new LinkedHashMap<>();
        for (Endpoint endpoint : endpoints) {
            endpoint.prepare().run();
            measure(endpoint.action());
            endpoint.prepare().run();
            results.put(endpoint.label(), measure(endpoint.action()));
        }
        return results;
    }

    private Measurement measure(HttpAction action) {
        long sqlBefore = SqlCountingConfig.sqlCount();
        long start = System.nanoTime();
        int httpStatus;
        try {
            httpStatus = action.run();
        } catch (Exception e) {
            httpStatus = -1;
        }
        return new Measurement(
                httpStatus,
                SqlCountingConfig.sqlCount() - sqlBefore,
                (System.nanoTime() - start) / 1_000_000);
    }

    private String buildReport(
            List<Endpoint> endpoints, Map<String, Measurement> few, Map<String, Measurement> many, boolean adminCovered) {
        List<Endpoint> sorted = endpoints.stream()
                .sorted(Comparator.comparingLong((Endpoint e) -> many.get(e.label()).sql()).reversed())
                .toList();

        StringBuilder out = new StringBuilder();
        out.append("[SQL-COUNT] số câu SQL mỗi endpoint: ít dữ liệu (")
                .append(FEW_MEMBERS + 1).append(" thành viên, ").append(FEW_TRANSACTIONS)
                .append(" giao dịch nhóm) -> nhiều dữ liệu (")
                .append(FEW_MEMBERS + MORE_MEMBERS + 1).append(" thành viên, ")
                .append(FEW_TRANSACTIONS + MORE_TRANSACTIONS).append(" giao dịch nhóm)\n");
        if (!adminCovered)
            out.append("[SQL-COUNT] CẢNH BÁO: không đăng nhập được tài khoản admin nên bỏ qua các endpoint /admin\n");
        out.append(String.format("%-46s %5s %8s %10s %6s %7s  %s%n",
                "endpoint", "http", "sql_it", "sql_nhieu", "tang", "ms", "ghi_chu"));
        for (Endpoint endpoint : sorted) {
            Measurement a = few.get(endpoint.label());
            Measurement b = many.get(endpoint.label());
            long growth = b.sql() - a.sql();
            String note = b.status() != 200 ? "LỖI HTTP" : growth > 0 ? "NGHI N+1 (SQL tăng theo dữ liệu)" : "";
            out.append(String.format("%-46s %5d %8d %10d %+6d %7d  %s%n",
                    endpoint.label(), b.status(), a.sql(), b.sql(), growth, b.ms(), note));
        }
        return out.toString();
    }

    private HttpAction getAction(String path, String token) {
        return () -> mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();
    }

    private HttpAction loginAction(String email, String password) {
        return () -> postJson("/auth/login", null, Map.of("email", email, "password", password));
    }

    private int postJson(String path, String token, Object body) throws Exception {
        return perform(path, token, body).getStatus();
    }

    private org.springframework.mock.web.MockHttpServletResponse perform(String path, String token, Object body)
            throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        if (token != null)
            request.header("Authorization", "Bearer " + token);
        return mockMvc.perform(request).andReturn().getResponse();
    }

    private Map<?, ?> dataOf(org.springframework.mock.web.MockHttpServletResponse response) throws Exception {
        Map<?, ?> parsed = objectMapper.readValue(response.getContentAsString(), Map.class);
        return (Map<?, ?>) parsed.get("data");
    }

    private String currentUserId(String token) throws Exception {
        String response = mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        return (String) ((Map<?, ?>) objectMapper.readValue(response, Map.class).get("data")).get("id");
    }

    private String loginOrNull(String email, String password) {
        try {
            return authSupport.loginAndGetAccessToken(email, password);
        } catch (Throwable e) {
            log.warn("Không đăng nhập được {}: {}", email, e.toString());
            return null;
        }
    }

    private String loginAndGetRefreshToken(String email, String password) {
        try {
            var response = perform("/auth/login", null, Map.of("email", email, "password", password));
            return (String) dataOf(response).get("refresh_token");
        } catch (Exception e) {
            log.warn("Không lấy được refresh token: {}", e.toString());
            return null;
        }
    }

    // đăng ký và xác thực từng thành viên bằng đúng luồng thật, trả về id người dùng
    private List<String> registerMembers(int startIndex, int count) throws Exception {
        List<String> ids = new ArrayList<>();
        for (int i = startIndex; i < startIndex + count; i++) {
            String token = authSupport.registerAndGetAccessToken("do.sql.member" + i + "@example.com");
            ids.add(currentUserId(token));
        }
        return ids;
    }

    private String createGroup(String ownerToken, List<String> memberIds) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Nhóm đo SQL");
        body.put("description", "Dữ liệu mẫu để đếm câu SQL");
        body.put("target", 10_000_000);
        body.put("is_settlement_enabled", true);
        body.put("is_join_without_confirm", true);
        body.put("members", memberIds);
        var response = perform("/groups", ownerToken, body);
        return (String) dataOf(response).get("id");
    }

    private void addGroupMembers(String ownerToken, String groupId, List<String> memberIds) throws Exception {
        postJson("/groups/" + groupId + "/members", ownerToken, Map.of("member_ids", memberIds));
    }

    // không assert trạng thái: dữ liệu mẫu sai thì báo cáo vẫn ra, chỉ log số giao dịch tạo được
    private int seedGroupTransactions(
            String ownerToken, String groupId, String ownerId, String categoryId, int alreadyCreated, int count)
            throws Exception {
        LocalDate today = LocalDate.now(VIETNAM_ZONE);
        int created = 0;
        for (int i = alreadyCreated; i < alreadyCreated + count; i++) {
            boolean contribution = i % 5 == 0;
            Map<String, Object> body = new HashMap<>();
            body.put("type", contribution ? "CONTRIBUTION" : "EXPENSE");
            body.put("money_source", "PERSONAL");
            body.put("amount", 10_000 + i);
            body.put("date", today.minusDays(i % 20).toString());
            body.put("transactor_id", ownerId);
            body.put("note", "Giao dịch nhóm đo SQL " + i);
            if (!contribution)
                body.put("category_id", categoryId);
            if (postJson("/groups/" + groupId + "/transactions", ownerToken, body) == 201)
                created++;
        }
        log.warn("[SQL-COUNT] tạo được {}/{} giao dịch nhóm", created, count);
        return alreadyCreated + count;
    }

    // dạng response có thể khác dự kiến, nên không lấy được id thì bỏ qua endpoint chi tiết
    private String firstGroupTransactionId(String ownerToken, String groupId) {
        try {
            String response = mockMvc.perform(get("/groups/" + groupId + "/transactions")
                            .header("Authorization", "Bearer " + ownerToken))
                    .andReturn().getResponse().getContentAsString();
            Map<?, ?> data = (Map<?, ?>) objectMapper.readValue(response, Map.class).get("data");
            List<?> items = (List<?>) data.get("items");
            return (String) ((Map<?, ?>) items.get(0)).get("id");
        } catch (Exception e) {
            log.warn("Không lấy được id giao dịch nhóm đầu tiên, bỏ qua endpoint chi tiết: {}", e.toString());
            return null;
        }
    }

    private String systemCategoryId(String type) {
        return jdbcTemplate.queryForObject(
                "SELECT id::text FROM categories WHERE user_id IS NULL AND type = ? LIMIT 1", String.class, type);
    }

    @FunctionalInterface
    private interface HttpAction {
        int run() throws Exception;
    }

    // prepare chạy trước mỗi lần gọi và không được tính vào số đo (vd lấy refresh token mới)
    private record Endpoint(String label, Runnable prepare, HttpAction action) {
        static Endpoint of(String label, HttpAction action) {
            return new Endpoint(label, () -> { }, action);
        }
    }

    private record Measurement(int status, long sql, long ms) {
    }
}

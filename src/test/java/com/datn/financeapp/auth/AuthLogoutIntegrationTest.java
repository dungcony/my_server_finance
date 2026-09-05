package com.datn.financeapp.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * AUTH-04 — {@code POST /auth/logout} phải idempotent về mặt hành vi: luôn trả 200, không ném lỗi
 * dù thẻ không tồn tại, đã thu hồi, hay client không gửi thẻ nào cả.
 *
 * <p>Bộ test này sinh ra từ một lỗi thật (FIX-01, đợt test 02/09/2026): api/01 mục 4 không đặc tả
 * trường mang refresh token trong body, nên app Flutter chỉ gửi {@code logout_all_devices}.
 * Backend băm thẳng giá trị null và ném {@code NullPointerException} → 500. Hậu quả ở phía app
 * còn tệ hơn mã lỗi: lời gọi mạng ném ngoại lệ trước khi kịp xoá trạng thái đăng nhập, người
 * dùng bấm Đăng xuất thì màn hình đứng im và phải tắt hẳn ứng dụng mới thoát ra được.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthLogoutIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1sb2dvdXQtaW50ZWdyYXRpb24tdGVzdA==");
    }

    @TestConfiguration
    static class NoRateLimitConfig {
        // Tên method PHẢI khớp "rateLimitFilter" — xem AuthRegisterLoginIntegrationTest.
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

    @BeforeEach
    void cleanTables() {
        refreshTokenRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    /** Đăng ký một tài khoản mới, trả về cặp thẻ của phiên vừa mở. */
    private Map<?, ?> register(String email) throws Exception {
        Map<String, Object> body = Map.of(
                "email", email,
                "password", "matkhaudung1",
                "username", "Người Test Logout");

        String response = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return (Map<?, ?>) objectMapper.readValue(response, Map.class).get("data");
    }

    /** Mở thêm một phiên nữa cho tài khoản đã có — giả lập đăng nhập trên thiết bị thứ hai. */
    private Map<?, ?> login(String email) throws Exception {
        Map<String, Object> body = Map.of("email", email, "password", "matkhaudung1");

        String response = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return (Map<?, ?>) objectMapper.readValue(response, Map.class).get("data");
    }

    private void logout(String accessToken, Map<String, Object> body) throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    void logoutKhongGuiRefreshTokenVanTra200() throws Exception {
        Map<?, ?> session = register("logout.khong.token@datn.local");

        // Đúng những gì app Flutter gửi: chỉ có logout_all_devices, không kèm thẻ.
        // Trước khi sửa, chính lời gọi này trả 500 NullPointerException.
        logout((String) session.get("access_token"), Map.of("logout_all_devices", false));
    }

    @Test
    void logoutGuiRefreshTokenThiThuHoiDungTheDo() throws Exception {
        String email = "logout.co.token@datn.local";
        Map<?, ?> session = register(email);
        String refreshToken = (String) session.get("refresh_token");

        logout(
                (String) session.get("access_token"),
                Map.of("refresh_token", refreshToken, "logout_all_devices", false));

        // Thẻ đã thu hồi thì không đổi lấy phiên mới được nữa.
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refresh_token", refreshToken))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutAllDevicesThuHoiMoiPhienDuKhongGuiToken() throws Exception {
        String email = "logout.all.devices@datn.local";
        Map<?, ?> deviceOne = register(email);
        Map<?, ?> deviceTwo = login(email);

        String tokenOfDeviceOne = (String) deviceOne.get("refresh_token");

        // Thiết bị hai đăng xuất toàn bộ nhưng KHÔNG gửi thẻ của mình. Danh tính lấy từ JWT,
        // nên vẫn phải thu hồi được cả phiên của thiết bị một.
        logout((String) deviceTwo.get("access_token"), Map.of("logout_all_devices", true));

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refresh_token", tokenOfDeviceOne))))
                .andExpect(status().isUnauthorized());

        assertThat(refreshTokenRepository.findAll())
                .as("mọi thẻ của tài khoản phải ở trạng thái đã thu hồi")
                .allMatch(token -> token.getRevokedAt() != null);
    }

    @Test
    void logoutHaiLanLienTiepVanTra200() throws Exception {
        Map<?, ?> session = register("logout.hai.lan@datn.local");
        String accessToken = (String) session.get("access_token");
        String refreshToken = (String) session.get("refresh_token");

        Map<String, Object> body = Map.of("refresh_token", refreshToken, "logout_all_devices", false);

        logout(accessToken, body);
        // Lần thứ hai trên thẻ đã thu hồi — idempotent, không được đổi thành lỗi.
        logout(accessToken, body);
    }
}

package com.datn.financeapp.auth;

import com.datn.financeapp.auth.enums.OtpType;
import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.auth.dto.request.RegisterRequest;
import com.datn.financeapp.auth.dto.request.ResendVerificationRequest;
import com.datn.financeapp.auth.dto.request.VerifyEmailRequest;
import com.datn.financeapp.auth.dto.response.AuthResponse;
import com.datn.financeapp.auth.entity.OtpModel;
import com.datn.financeapp.auth.repository.OtpRepository;
import com.datn.financeapp.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.auth.service.AuthService;
import com.datn.financeapp.common.ratelimit.RateLimitFilter;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.enums.UserStatus;
import com.datn.financeapp.user.repository.UserRepository;
import com.datn.financeapp.wallet.repository.WalletRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(TestRedisConfig.class)
class AuthEmailVerificationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1hdXRoLXZlcmlmeS10ZXN0");
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
    private OtpRepository otpRepository;

    @Autowired
    private AuthService authService;

    @BeforeEach
    void cleanTables() {
        otpRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void register_savesOtpInRedis_andVerifyEmailSuccess() throws Exception {
        String email = "xac.thuc@example.com";
        authService.register(new RegisterRequest(email, "matkhau123", "Người Dùng Mới"));

        // Kiểm tra User ban đầu chưa kích hoạt
        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_VERIFY);
        assertThat(user.isConfirm()).isFalse();

        // Kiểm tra OTP đã được lưu vào Redis
        Optional<OtpModel> otpOpt = otpRepository.findByTypeAndEmail(OtpType.REGISTER_OTP, email);
        assertThat(otpOpt).isPresent();
        String savedCode = otpOpt.get().getCode();
        assertThat(savedCode).matches("^\\d{6}$");

        // Gọi API xác thực email với mã đúng
        VerifyEmailRequest verifyReq = new VerifyEmailRequest(email, savedCode);
        mockMvc.perform(post("/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.email").value(email))
                // `status` thay cho cờ `is_confirm` cũ (hợp đồng mới 13/09/2026, app đã theo).
                .andExpect(jsonPath("$.data.user.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.access_token").isNotEmpty())
                .andExpect(jsonPath("$.data.refresh_token").isNotEmpty());

        // Kiểm tra sau khi xác thực: User đã ACTIVE và OTP trong Redis bị xóa
        User updated = userRepository.findByEmail(email).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(updated.isConfirm()).isTrue();
        assertThat(otpRepository.findByTypeAndEmail(OtpType.REGISTER_OTP, email)).isEmpty();
    }

    @Test
    void verifyEmail_wrongCode_returnsBadRequest() throws Exception {
        String email = "sai.ma@example.com";
        authService.register(new RegisterRequest(email, "matkhau123", "Người Dùng"));

        VerifyEmailRequest verifyReq = new VerifyEmailRequest(email, "000000");
        mockMvc.perform(post("/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VERIFICATION_CODE_INVALID"));

        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(user.isConfirm()).isFalse();
    }

    @Test
    void verifyEmail_codeExpiredOrNonExistent_returnsBadRequest() throws Exception {
        VerifyEmailRequest verifyReq = new VerifyEmailRequest("khong.ton.tai@example.com", "123456");
        mockMvc.perform(post("/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VERIFICATION_CODE_INVALID"));
    }

    @Test
    void resendVerification_withinCooldown_returnsRateLimited() throws Exception {
        String email = "gui.lai@example.com";
        authService.register(new RegisterRequest(email, "matkhau123", "Người Dùng"));

        // Gửi lại ngay lập tức (chưa đủ 60s cooldown)
        ResendVerificationRequest resendReq = new ResendVerificationRequest(email);
        mockMvc.perform(post("/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resendReq)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void resendVerification_afterCooldown_generatesNewCode() throws Exception {
        String email = "gui.lai.thanh.cong@example.com";
        authService.register(new RegisterRequest(email, "matkhau123", "Người Dùng"));

        // Giả lập OTP cũ đã tạo từ 70 giây trước
        OtpModel oldOtp = otpRepository.findByTypeAndEmail(OtpType.REGISTER_OTP, email).orElseThrow();
        oldOtp.setCreatedAt(java.time.Instant.now().minusSeconds(70));
        otpRepository.save(oldOtp);

        ResendVerificationRequest resendReq = new ResendVerificationRequest(email);
        mockMvc.perform(post("/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resendReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").isNotEmpty());

        Optional<OtpModel> newOtpOpt = otpRepository.findByTypeAndEmail(OtpType.REGISTER_OTP, email);
        assertThat(newOtpOpt).isPresent();
        assertThat(newOtpOpt.get().getCode()).matches("^\\d{6}$");
    }
}

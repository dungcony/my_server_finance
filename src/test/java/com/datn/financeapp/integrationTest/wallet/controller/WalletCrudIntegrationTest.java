package com.datn.financeapp.integrationTest.wallet.controller;

import com.datn.financeapp.module.wallet.controller.*;


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
        otpRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM transactions");
        refreshTokenRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String registerAndGetAccessToken(String email) throws Exception {
        return authSupport.registerAndGetAccessToken(email);
    }

    private String findDefaultWalletId(String token) throws Exception {
        String response = mockMvc.perform(get("/wallets").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Map<?, ?> data = (Map<?, ?>) objectMapper.readValue(response, Map.class).get("data");
        List<?> wallets = (List<?>) data.get("content");
        for (Object w : wallets) {
            String id = (String) ((Map<?, ?>) w).get("id");
            String detailResp = mockMvc.perform(get("/wallets/" + id).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            Map<?, ?> detail = (Map<?, ?>) ((Map<?, ?>) objectMapper.readValue(detailResp, Map.class)).get("data");
            if (Boolean.TRUE.equals(detail.get("is_default"))) {
                return id;
            }
        }
        throw new IllegalStateException("Default wallet not found");
    }

    private String createWallet(String token, String name, String type, long balance) throws Exception {
        Map<String, Object> body = Map.of(
                "name", name,
                "type", type.toUpperCase(),
                "balance", balance,
                "icon_code", "default_wallet",
                "color", "#2f8272");
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
    void createWallet_newName_returns201WithBalanceAndName() throws Exception {
        String token = registerAndGetAccessToken("tao.vi@example.com");

        Map<String, Object> body = Map.of(
                "name", "Vietcombank",
                "type", "BANK",
                "balance", 5_000_000,
                "icon_code", "default_wallet",
                "color", "#2f8272");

        mockMvc.perform(post("/wallets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.balance").value(5_000_000))
                .andExpect(jsonPath("$.data.name").value("Vietcombank"));

        mockMvc.perform(get("/wallets").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2));
    }

    @Test
    void createWallet_duplicateName_returns409WalletNameExists() throws Exception {
        String token = registerAndGetAccessToken("trung.ten@example.com");
        createWallet(token, "Ví Chung", "BANK", 1_000_000);

        Map<String, Object> body = Map.of(
                "name", "Ví Chung",
                "type", "CASH",
                "balance", 0,
                "icon_code", "default_wallet",
                "color", "#2f8272");

        mockMvc.perform(post("/wallets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WALLET_NAME_EXISTS"));
    }

    @Test
    void register_newUser_hasExactlyOneDefaultCashWallet() throws Exception {
        String token = registerAndGetAccessToken("vi.mac.dinh@example.com");

        String defaultId = findDefaultWalletId(token);
        mockMvc.perform(get("/wallets/" + defaultId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Tiền mặt"))
                .andExpect(jsonPath("$.data.type").value("CASH"))
                .andExpect(jsonPath("$.data.is_default").value(true));
    }

    @Test
    void createWallet_additionalWallet_isNotDefault() throws Exception {
        String token = registerAndGetAccessToken("vi.them@example.com");
        String walletId = createWallet(token, "Ví Thêm", "BANK", 0);

        mockMvc.perform(get("/wallets/" + walletId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.is_default").value(false));
    }

    @Test
    void deleteWallet_defaultWallet_returns409DefaultNotDeletable() throws Exception {
        String token = registerAndGetAccessToken("xoa.mac.dinh@example.com");
        createWallet(token, "Ví Phụ", "BANK", 0);
        String defaultId = findDefaultWalletId(token);

        mockMvc.perform(delete("/wallets/" + defaultId).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WALLET_DEFAULT_NOT_DELETABLE"));
    }

    @Test
    void deleteWallet_nonDefaultWallet_successCascadesSoftDelete() throws Exception {
        String token = registerAndGetAccessToken("xoa.hop.le@example.com");
        String walletId = createWallet(token, "Ví Cần Xóa", "BANK", 1_000_000);

        mockMvc.perform(delete("/wallets/" + walletId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/wallets/" + walletId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void patchWallet_updateNameAndColor_success() throws Exception {
        String token = registerAndGetAccessToken("sua.ten@example.com");
        String walletId = createWallet(token, "Ví Cũ", "BANK", 0);

        Map<String, Object> body = Map.of(
                "name", "Ví Mới",
                "color", "#334455");

        mockMvc.perform(patch("/wallets/" + walletId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Ví Mới"))
                .andExpect(jsonPath("$.data.color").value("#334455"));
    }
}

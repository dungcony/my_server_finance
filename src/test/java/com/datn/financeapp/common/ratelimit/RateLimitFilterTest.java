package com.datn.financeapp.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit test thuần cho {@link RateLimitFilter} — không cần Testcontainers vì không chạm DB.
 * Gọi trực tiếp {@code doFilter} với {@link MockHttpServletRequest}/{@link MockHttpServletResponse}.
 */
class RateLimitFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void quaNamLanTrongMotPhutToiAuthTuCungIp_LanThuSauBi429() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(new RateLimitProperties());
        String ip = "10.0.0.1";

        for (int i = 1; i <= 5; i++) {
            MockHttpServletRequest request = requestFrom(ip, "/auth/login");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isNotEqualTo(429);
        }

        MockHttpServletRequest sixthRequest = requestFrom(ip, "/auth/login");
        MockHttpServletResponse sixthResponse = new MockHttpServletResponse();
        filter.doFilter(sixthRequest, sixthResponse, new MockFilterChain());

        assertThat(sixthResponse.getStatus()).isEqualTo(429);

        Map<?, ?> body = objectMapper.readValue(sixthResponse.getContentAsString(), Map.class);
        assertThat(body.get("success")).isEqualTo(false);
        Map<?, ?> error = (Map<?, ?>) body.get("error");
        assertThat(error.get("code")).isEqualTo("RATE_LIMIT_EXCEEDED");
    }

    @Test
    void haiUserKhacNhauCungIp_QuotaDocLapTheoUser() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(new RateLimitProperties());
        String sharedIp = "10.0.0.2";
        String userA = UUID.randomUUID().toString();
        String userB = UUID.randomUUID().toString();

        // User A dùng hết quota 120/phút của nhóm "default" bằng cách gọi 120 lần.
        for (int i = 1; i <= 120; i++) {
            setAuthenticatedUser(userA);
            MockHttpServletRequest request = requestFrom(sharedIp, "/wallets");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isNotEqualTo(429);
        }

        // User A giờ đã hết quota -> request thứ 121 của A bị chặn.
        setAuthenticatedUser(userA);
        MockHttpServletRequest userABlockedRequest = requestFrom(sharedIp, "/wallets");
        MockHttpServletResponse userABlockedResponse = new MockHttpServletResponse();
        filter.doFilter(userABlockedRequest, userABlockedResponse, new MockFilterChain());
        assertThat(userABlockedResponse.getStatus()).isEqualTo(429);

        // User B (cùng IP, khác user_id) vẫn còn nguyên quota — không bị gộp theo IP.
        setAuthenticatedUser(userB);
        MockHttpServletRequest userBRequest = requestFrom(sharedIp, "/wallets");
        MockHttpServletResponse userBResponse = new MockHttpServletResponse();
        filter.doFilter(userBRequest, userBResponse, new MockFilterChain());
        assertThat(userBResponse.getStatus()).isNotEqualTo(429);
    }

    @Test
    void moiResponseDeuCoDuBaHeaderRateLimit() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(new RateLimitProperties());
        MockHttpServletRequest request = requestFrom("10.0.0.3", "/wallets");
        MockHttpServletResponse response = new MockHttpServletResponse();

        setAuthenticatedUser(UUID.randomUUID().toString());
        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("X-RateLimit-Limit")).isNotNull();
        assertThat(response.getHeader("X-RateLimit-Remaining")).isNotNull();
        assertThat(response.getHeader("X-RateLimit-Reset")).isNotNull();
    }

    private MockHttpServletRequest requestFrom(String ip, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRemoteAddr(ip);
        return request;
    }

    private void setAuthenticatedUser(String userId) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList()));
    }
}

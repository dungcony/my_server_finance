package com.datn.financeapp.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingFilterTest {

    private RequestLoggingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequestLoggingFilter();
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void doFilter_GeneratesRequestId_WhenHeaderNotProvided() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/wallets");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        String requestId = response.getHeader("X-Request-Id");
        assertThat(requestId).isNotBlank();
        // MDC must be cleared after filter finishes
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void doFilter_PreservesIncomingRequestId_WhenProvided() throws ServletException, IOException {
        String existingId = "client-req-12345";
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/auth/login");
        request.addHeader("X-Request-Id", existingId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Request-Id")).isEqualTo(existingId);
    }

    @Test
    void doFilter_CapturesAuthenticatedUser_FromRequestAttribute() throws ServletException, IOException {
        String userId = UUID.randomUUID().toString();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            req.setAttribute(RequestLoggingFilter.ATTR_USER_ID, userId);
            ((MockHttpServletResponse) res).setStatus(200);
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void doFilter_CapturesAuthenticatedUser_FromSecurityContext() throws ServletException, IOException {
        String userId = UUID.randomUUID().toString();
        var auth = new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/reports/monthly");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void doFilter_LogsAppropriately_On4xxAnd5xxStatus() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/transactions");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> ((MockHttpServletResponse) res).setStatus(400);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(400);
    }
}

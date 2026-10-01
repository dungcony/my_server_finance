package com.datn.financeapp.group.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datn.financeapp.common.exception.GlobalExceptionHandler;
import com.datn.financeapp.group.dto.request.fund.FundKepperUpdateReq;
import com.datn.financeapp.group.dto.request.fund.FundReconcileReq;
import com.datn.financeapp.group.dto.response.fund.GroupFundReconcileRes;
import com.datn.financeapp.group.dto.response.fund.FundRes;
import com.datn.financeapp.group.enums.TransactionType;
import com.datn.financeapp.group.service.FundService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class FundControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @Mock
    private FundService fundService;

    @InjectMocks
    private FundController fundController;

    private final UUID userId = UUID.randomUUID();
    private final UUID groupId = UUID.randomUUID();
    private final UUID fundId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(fundController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(converter)
                .build();

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(userId.toString(), null, Collections.emptyList())
        );
        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("PUT /groups/{groupId}/fund-kepper - Cập nhật thủ quỹ thành công")
    void updateFund_success() throws Exception {
        UUID newKeepperId = UUID.randomUUID();
        FundKepperUpdateReq req = new FundKepperUpdateReq(newKeepperId);
        FundRes res = new FundRes(fundId, groupId, newKeepperId, 500_000L, Instant.now());

        when(fundService.updateFundKeepper(eq(userId), eq(groupId), any(FundKepperUpdateReq.class))).thenReturn(res);

        mockMvc.perform(put("/groups/{groupId}/fund-kepper", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.keepper_id").value(newKeepperId.toString()));

        verify(fundService).updateFundKeepper(eq(userId), eq(groupId), any(FundKepperUpdateReq.class));
    }

    @Test
    @DisplayName("POST /groups/{groupId}/fund/reconcile - Kiểm kê số dư quỹ thành công")
    void reconcileFund_success() throws Exception {
        FundReconcileReq req = new FundReconcileReq(600_000L, LocalDate.now(), "Kiểm quỹ tháng", null);
        GroupFundReconcileRes res = new GroupFundReconcileRes(
                500_000L, 600_000L, 100_000L, TransactionType.ADJUSTMENT_UP, UUID.randomUUID()
        );

        when(fundService.reconcileFund(eq(userId), eq(groupId), any(FundReconcileReq.class))).thenReturn(res);

        mockMvc.perform(post("/groups/{groupId}/fund/reconcile", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.difference").value(100000L));

        verify(fundService).reconcileFund(eq(userId), eq(groupId), any(FundReconcileReq.class));
    }
}

package com.datn.financeapp.group.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datn.financeapp.common.exception.GlobalExceptionHandler;
import com.datn.financeapp.group.dto.response.report.GroupBalanceReportRes;
import com.datn.financeapp.group.dto.response.report.GroupSummaryReportRes;
import com.datn.financeapp.group.service.ReportService;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class GroupReportControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @Mock
    private ReportService reportService;

    @InjectMocks
    private GroupReportController groupReportController;

    private final UUID userId = UUID.randomUUID();
    private final UUID groupId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(groupReportController)
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
    @DisplayName("GET /groups/{groupId}/summary - Báo cáo tổng quan nhóm")
    void summaryReport_success() throws Exception {
        GroupSummaryReportRes res = new GroupSummaryReportRes(
                groupId, "Nhóm Du Lịch", 5_000_000L, "2026-09", null, 1_000_000L, 2_000_000L
        );
        when(reportService.getSummary(userId, groupId, "2026-09")).thenReturn(res);

        mockMvc.perform(get("/groups/{groupId}/summary", groupId)
                        .param("month", "2026-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.group_name").value("Nhóm Du Lịch"))
                .andExpect(jsonPath("$.data.total_expense").value(1000000L));

        verify(reportService).getSummary(userId, groupId, "2026-09");
    }

    @Test
    @DisplayName("GET /groups/{groupId}/balances - Báo cáo số dư thành viên")
    void balancesReport_success() throws Exception {
        GroupBalanceReportRes res = new GroupBalanceReportRes(
                groupId, 5_000_000L, 500_000L, 4_500_000L, true, Collections.emptyList()
        );
        when(reportService.getBalances(userId, groupId)).thenReturn(res);

        mockMvc.perform(get("/groups/{groupId}/balances", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.group_id").value(groupId.toString()))
                .andExpect(jsonPath("$.data.is_settlement_enabled").value(true));

        verify(reportService).getBalances(userId, groupId);
    }
}

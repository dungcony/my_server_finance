package com.datn.financeapp.unitTest.group.controller;

import com.datn.financeapp.module.group.controller.*;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datn.financeapp.common.exception.GlobalExceptionHandler;
import com.datn.financeapp.common.response.PageMeta;
import com.datn.financeapp.module.group.dto.request.transaction.BatchGroupTxnUpdateStatusReq;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTxnCreateReq;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTxnParticipantReq;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTxnFilterReq;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTxnUpdateReq;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTxnUpdateStatusReq;
import com.datn.financeapp.module.group.dto.response.transaction.GroupTxnDetailRes;
import com.datn.financeapp.module.group.dto.response.transaction.GroupTxnListRes;
import com.datn.financeapp.module.group.dto.response.transaction.GroupTxnParticipantRes;
import com.datn.financeapp.module.group.dto.response.transaction.GroupTxnSumaryRes;
import com.datn.financeapp.module.group.enums.GroupTxnStatus;
import com.datn.financeapp.module.group.enums.GroupTxnType;
import com.datn.financeapp.module.group.enums.MoneySource;
import com.datn.financeapp.module.group.service.GroupTxnCreateService;
import com.datn.financeapp.module.group.service.GroupTxnDeleteService;
import com.datn.financeapp.module.group.service.GroupTxnGetService;
import com.datn.financeapp.module.group.service.GroupTxnUpdateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class GroupTxnMapperControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @Mock
    private GroupTxnGetService groupTxnGetService;
    @Mock
    private GroupTxnCreateService groupTxnCreateService;
    @Mock
    private GroupTxnUpdateService groupTxnUpdateService;
    @Mock
    private GroupTxnDeleteService groupTxnDeleteService;

    @InjectMocks
    private GroupTxnController groupTxnController;

    private final UUID userId = UUID.randomUUID();
    private final UUID groupId = UUID.randomUUID();
    private final UUID txnId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(groupTxnController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
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
    @DisplayName("POST /groups/{groupId}/transactions - Tạo giao dịch nhóm thành công trả về 201")
    void createTransaction_success() throws Exception {
        GroupTxnCreateReq req = new GroupTxnCreateReq(
                GroupTxnType.EXPENSE, MoneySource.FUND, 150_000L,
                Instant.now(), "an_uong", null, "Ăn trưa nhóm",
                List.of(new GroupTxnParticipantReq(userId, 150_000L))
        );

        GroupTxnDetailRes res = new GroupTxnDetailRes(
                txnId, groupId, userId, userId, req.categoryCode(),
                MoneySource.FUND, GroupTxnType.EXPENSE, GroupTxnStatus.PENDING,
                null, null, 150_000L, Instant.now(), "Ăn trưa nhóm",
                Instant.now(), Instant.now()
        );

        when(groupTxnCreateService.create(eq(groupId), any(GroupTxnCreateReq.class))).thenReturn(res);

        mockMvc.perform(post("/groups/{groupId}/transactions", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(txnId.toString()))
                .andExpect(jsonPath("$.data.amount").value(150000L));

        verify(groupTxnCreateService).create(eq(groupId), any(GroupTxnCreateReq.class));
    }

    @Test
    @DisplayName("GET /groups/{groupId}/transactions - Danh sách giao dịch nhóm phân trang")
    void listTransactions_success() throws Exception {
        GroupTxnSumaryRes res = new GroupTxnSumaryRes(
                txnId, userId, userId, "an_uong", GroupTxnType.EXPENSE, GroupTxnStatus.CONFIRMED, 150_000L
        );
        GroupTxnListRes listRes = GroupTxnListRes.of(List.of(res), new PageMeta(1, 20, 1L, 1));

        when(groupTxnGetService.generalList(eq(groupId), any(), any(Pageable.class))).thenReturn(listRes);

        mockMvc.perform(get("/groups/{groupId}/transactions", groupId)
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].txn_id").value(txnId.toString()));

        verify(groupTxnGetService).generalList(eq(groupId), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /groups/{groupId}/transactions/{txnId} - Xem chi tiết giao dịch")
    void detailTransaction_success() throws Exception {
        GroupTxnDetailRes res = new GroupTxnDetailRes(
                txnId, groupId, userId, userId, null,
                MoneySource.FUND, GroupTxnType.EXPENSE, GroupTxnStatus.CONFIRMED,
                null, null, 150_000L, Instant.now(), "Chi tiết",
                Instant.now(), Instant.now()
        );
        when(groupTxnGetService.detail(groupId, txnId)).thenReturn(res);

        mockMvc.perform(get("/groups/{groupId}/transactions/{txnId}", groupId, txnId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(txnId.toString()));

        verify(groupTxnGetService).detail(groupId, txnId);
    }

    @Test
    @DisplayName("GET /groups/{groupId}/transactions/{txnId}/participants - Lấy danh sách người tham gia giao dịch")
    void getParticipants_success() throws Exception {
        GroupTxnParticipantRes pRes = new GroupTxnParticipantRes(userId, 75_000L);
        when(groupTxnGetService.getParticipants(groupId, txnId)).thenReturn(List.of(pRes));

        mockMvc.perform(get("/groups/{groupId}/transactions/{txnId}/participants", groupId, txnId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].user_id").value(userId.toString()))
                .andExpect(jsonPath("$.data[0].share_amount").value(75000L));

        verify(groupTxnGetService).getParticipants(groupId, txnId);
    }

    @Test
    @DisplayName("PUT /groups/{groupId}/transactions/{txnId} - Cập nhật giao dịch")
    void updateTransaction_success() throws Exception {
        GroupTxnUpdateReq req = new GroupTxnUpdateReq(
                txnId, 200_000L, null, null, MoneySource.FUND, Instant.now(), null, userId, "Ghi chú mới", null
        );
        GroupTxnDetailRes res = new GroupTxnDetailRes(
                txnId, groupId, userId, userId, null,
                MoneySource.FUND, GroupTxnType.EXPENSE, GroupTxnStatus.PENDING,
                null, null, 200_000L, Instant.now(), "Ghi chú mới",
                Instant.now(), Instant.now()
        );

        when(groupTxnUpdateService.update(eq(groupId), any(GroupTxnUpdateReq.class))).thenReturn(res);

        mockMvc.perform(put("/groups/{groupId}/transactions/{txnId}", groupId, txnId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.amount").value(200000L));

        verify(groupTxnUpdateService).update(eq(groupId), any(GroupTxnUpdateReq.class));
    }

    @Test
    @DisplayName("DELETE /groups/{groupId}/transactions/{txnId} - Xóa giao dịch")
    void deleteTransaction_success() throws Exception {
        mockMvc.perform(delete("/groups/{groupId}/transactions/{txnId}", groupId, txnId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(groupTxnDeleteService).softDelete(groupId, txnId);
    }

    @Test
    @DisplayName("POST /groups/{groupId}/transactions/status - Cập nhật trạng thái duyệt/từ chối")
    void statusTransaction_success() throws Exception {
        GroupTxnUpdateStatusReq req = new GroupTxnUpdateStatusReq(txnId, GroupTxnStatus.CONFIRMED, null);
        GroupTxnDetailRes res = new GroupTxnDetailRes(
                txnId, groupId, userId, userId, null,
                MoneySource.FUND, GroupTxnType.EXPENSE, GroupTxnStatus.CONFIRMED,
                userId, Instant.now(), 150_000L, Instant.now(), "Duyệt",
                Instant.now(), Instant.now()
        );
        when(groupTxnUpdateService.status(eq(groupId), any(GroupTxnUpdateStatusReq.class))).thenReturn(res);

        mockMvc.perform(post("/groups/{groupId}/transactions/status", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        verify(groupTxnUpdateService).status(eq(groupId), any(GroupTxnUpdateStatusReq.class));
    }

    @Test
    @DisplayName("POST /groups/{groupId}/transactions/statuses - Duyệt/từ chối hàng loạt")
    void bulkStatusesTransaction_success() throws Exception {
        BatchGroupTxnUpdateStatusReq req = new BatchGroupTxnUpdateStatusReq(List.of(txnId), GroupTxnStatus.CONFIRMED);

        mockMvc.perform(post("/groups/{groupId}/transactions/statuses", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(groupTxnUpdateService).statuses(eq(groupId), any(BatchGroupTxnUpdateStatusReq.class));
    }
}

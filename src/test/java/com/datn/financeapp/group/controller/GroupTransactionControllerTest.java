package com.datn.financeapp.group.controller;

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
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionBulkReviewReq;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionCreateReq;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionFilterReq;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionUpdateReq;
import com.datn.financeapp.group.dto.response.transaction.GroupTransactionDetailRes;
import com.datn.financeapp.group.dto.response.transaction.GroupTransactionListRes;
import com.datn.financeapp.group.dto.response.transaction.GroupTransactionParticipantRes;
import com.datn.financeapp.group.enums.GTransactionStatus;
import com.datn.financeapp.group.enums.GTransactionType;
import com.datn.financeapp.group.enums.MoneySource;
import com.datn.financeapp.group.service.GTransactionService;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class GroupTransactionControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @Mock
    private GTransactionService gTransactionService;

    @InjectMocks
    private GroupTransactionController groupTransactionController;

    private final UUID userId = UUID.randomUUID();
    private final UUID groupId = UUID.randomUUID();
    private final UUID txnId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(groupTransactionController)
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
        GroupTransactionCreateReq req = new GroupTransactionCreateReq(
                GTransactionType.EXPENSE, MoneySource.FUND, 150_000L,
                Instant.now(), null, UUID.randomUUID(), userId, "Ăn trưa nhóm", null
        );

        GroupTransactionDetailRes res = new GroupTransactionDetailRes(
                txnId, groupId, userId, userId, req.categoryId(),
                MoneySource.FUND, GTransactionType.EXPENSE, GTransactionStatus.PENDING,
                null, null, 150_000L, Instant.now(), "Ăn trưa nhóm",
                Instant.now(), Instant.now()
        );

        when(gTransactionService.create(eq(userId), eq(groupId), any(GroupTransactionCreateReq.class))).thenReturn(res);

        mockMvc.perform(post("/groups/{groupId}/transactions", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(txnId.toString()))
                .andExpect(jsonPath("$.data.amount").value(150000L));

        verify(gTransactionService).create(eq(userId), eq(groupId), any(GroupTransactionCreateReq.class));
    }

    @Test
    @DisplayName("GET /groups/{groupId}/transactions - Danh sách giao dịch nhóm phân trang")
    void listTransactions_success() throws Exception {
        GroupTransactionDetailRes res = new GroupTransactionDetailRes(
                txnId, groupId, userId, userId, null,
                MoneySource.FUND, GTransactionType.EXPENSE, GTransactionStatus.CONFIRMED,
                userId, Instant.now(), 150_000L, Instant.now(), "Ăn trưa",
                Instant.now(), Instant.now()
        );
        GroupTransactionListRes listRes = GroupTransactionListRes.of(List.of(res), new PageMeta(1, 20, 1L, 1));

        when(gTransactionService.list(eq(userId), eq(groupId), any(GroupTransactionFilterReq.class))).thenReturn(listRes);

        mockMvc.perform(get("/groups/{groupId}/transactions", groupId)
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].id").value(txnId.toString()));

        verify(gTransactionService).list(eq(userId), eq(groupId), any(GroupTransactionFilterReq.class));
    }

    @Test
    @DisplayName("GET /groups/{groupId}/transactions/{txnId} - Xem chi tiết giao dịch")
    void detailTransaction_success() throws Exception {
        GroupTransactionDetailRes res = new GroupTransactionDetailRes(
                txnId, groupId, userId, userId, null,
                MoneySource.FUND, GTransactionType.EXPENSE, GTransactionStatus.CONFIRMED,
                null, null, 150_000L, Instant.now(), "Chi tiết",
                Instant.now(), Instant.now()
        );
        when(gTransactionService.detail(userId, groupId, txnId)).thenReturn(res);

        mockMvc.perform(get("/groups/{groupId}/transactions/{txnId}", groupId, txnId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(txnId.toString()));

        verify(gTransactionService).detail(userId, groupId, txnId);
    }

    @Test
    @DisplayName("GET /groups/{groupId}/transactions/{txnId}/participants - Lấy danh sách người tham gia giao dịch")
    void getParticipants_success() throws Exception {
        GroupTransactionParticipantRes pRes = new GroupTransactionParticipantRes(userId, 75_000L);
        when(gTransactionService.getParticipants(userId, groupId, txnId)).thenReturn(List.of(pRes));

        mockMvc.perform(get("/groups/{groupId}/transactions/{txnId}/participants", groupId, txnId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].user_id").value(userId.toString()))
                .andExpect(jsonPath("$.data[0].share_amount").value(75000L));

        verify(gTransactionService).getParticipants(userId, groupId, txnId);
    }

    @Test
    @DisplayName("PUT /groups/{groupId}/transactions/{txnId} - Cập nhật giao dịch")
    void updateTransaction_success() throws Exception {
        GroupTransactionUpdateReq req = new GroupTransactionUpdateReq(
                200_000L, Instant.now(), null, MoneySource.FUND, null, userId, "Ghi chú mới", null
        );
        GroupTransactionDetailRes res = new GroupTransactionDetailRes(
                txnId, groupId, userId, userId, null,
                MoneySource.FUND, GTransactionType.EXPENSE, GTransactionStatus.PENDING,
                null, null, 200_000L, Instant.now(), "Ghi chú mới",
                Instant.now(), Instant.now()
        );

        when(gTransactionService.update(eq(userId), eq(groupId), eq(txnId), any(GroupTransactionUpdateReq.class))).thenReturn(res);

        mockMvc.perform(put("/groups/{groupId}/transactions/{txnId}", groupId, txnId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.amount").value(200000L));

        verify(gTransactionService).update(eq(userId), eq(groupId), eq(txnId), any(GroupTransactionUpdateReq.class));
    }

    @Test
    @DisplayName("DELETE /groups/{groupId}/transactions/{txnId} - Xóa giao dịch")
    void deleteTransaction_success() throws Exception {
        mockMvc.perform(delete("/groups/{groupId}/transactions/{txnId}", groupId, txnId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(gTransactionService).delete(userId, groupId, txnId);
    }

    @Test
    @DisplayName("POST /groups/{groupId}/transactions/{txnId}/confirm - Phê duyệt giao dịch")
    void confirmTransaction_success() throws Exception {
        GroupTransactionDetailRes res = new GroupTransactionDetailRes(
                txnId, groupId, userId, userId, null,
                MoneySource.FUND, GTransactionType.EXPENSE, GTransactionStatus.CONFIRMED,
                userId, Instant.now(), 150_000L, Instant.now(), "Duyệt",
                Instant.now(), Instant.now()
        );
        when(gTransactionService.confirm(userId, groupId, txnId)).thenReturn(res);

        mockMvc.perform(post("/groups/{groupId}/transactions/{txnId}/confirm", groupId, txnId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        verify(gTransactionService).confirm(userId, groupId, txnId);
    }

    @Test
    @DisplayName("POST /groups/{groupId}/transactions/{txnId}/reject - Từ chối giao dịch")
    void rejectTransaction_success() throws Exception {
        GroupTransactionDetailRes res = new GroupTransactionDetailRes(
                txnId, groupId, userId, userId, null,
                MoneySource.FUND, GTransactionType.EXPENSE, GTransactionStatus.REJECTED,
                userId, Instant.now(), 150_000L, Instant.now(), "Từ chối",
                Instant.now(), Instant.now()
        );
        when(gTransactionService.reject(userId, groupId, txnId)).thenReturn(res);

        mockMvc.perform(post("/groups/{groupId}/transactions/{txnId}/reject", groupId, txnId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        verify(gTransactionService).reject(userId, groupId, txnId);
    }

    @Test
    @DisplayName("POST /groups/{groupId}/transactions/bulk-confirm - Phê duyệt hàng loạt")
    void bulkConfirm_success() throws Exception {
        GroupTransactionBulkReviewReq req = new GroupTransactionBulkReviewReq(List.of(txnId));

        mockMvc.perform(post("/groups/{groupId}/transactions/bulk-confirm", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(gTransactionService).bulkConfirm(eq(userId), eq(groupId), any(GroupTransactionBulkReviewReq.class));
    }

    @Test
    @DisplayName("POST /groups/{groupId}/transactions/bulk-reject - Từ chối hàng loạt")
    void bulkReject_success() throws Exception {
        GroupTransactionBulkReviewReq req = new GroupTransactionBulkReviewReq(List.of(txnId));

        mockMvc.perform(post("/groups/{groupId}/transactions/bulk-reject", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(gTransactionService).bulkReject(eq(userId), eq(groupId), any(GroupTransactionBulkReviewReq.class));
    }
}

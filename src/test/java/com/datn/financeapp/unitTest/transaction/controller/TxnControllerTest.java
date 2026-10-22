package com.datn.financeapp.unitTest.transaction.controller;

import com.datn.financeapp.common.exception.GlobalExceptionHandler;
import com.datn.financeapp.module.transaction.controller.TxnController;
import com.datn.financeapp.module.transaction.dto.request.TxnCreateReq;
import com.datn.financeapp.module.transaction.dto.request.TxnUpdateReq;
import com.datn.financeapp.module.transaction.dto.response.TxnDetailResponse;
import com.datn.financeapp.module.transaction.enums.TxnType;
import com.datn.financeapp.module.transaction.service.TxnBehavierService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TxnControllerTest {

    @Mock
    private TxnBehavierService txnBehavierService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        mockMvc = MockMvcBuilders.standaloneSetup(new TxnController(txnBehavierService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    @DisplayName("POST /transactions: Thieu type hoac walletId -> tra ve 400 Bad Request")
    void create_MissingRequiredFields_ReturnsValidationError() throws Exception {
        TxnCreateReq invalidReq = new TxnCreateReq(
                null,
                50_000L,
                null,
                "an_uong",
                Instant.now(),
                "Ăn trưa",
                "Ghi chú"
        );

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest());

        verify(txnBehavierService, never()).create(any(), any());
    }

    @Test
    @DisplayName("POST /transactions: So tien am -> tra ve 400 Bad Request")
    void create_NegativeAmount_ReturnsValidationError() throws Exception {
        TxnCreateReq invalidReq = new TxnCreateReq(
                TxnType.EXPENSE,
                -50_000L,
                UUID.randomUUID(),
                "an_uong",
                Instant.now(),
                "Ăn trưa",
                "Ghi chú"
        );

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest());

        verify(txnBehavierService, never()).create(any(), any());
    }

    @Test
    @DisplayName("POST /transactions: Tao giao dich hop le -> tra ve 201 Created")
    void create_Valid_ReturnsCreated() throws Exception {
        UUID txnId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        Instant now = Instant.now();

        TxnCreateReq req = new TxnCreateReq(
                TxnType.EXPENSE,
                100_000L,
                walletId,
                "an_uong",
                now,
                "Ăn trưa",
                "Phở"
        );

        TxnDetailResponse detail = new TxnDetailResponse(
                txnId,
                null,
                null,
                TxnType.EXPENSE,
                100_000L,
                now,
                "Ăn trưa",
                "Phở",
                now,
                now
        );

        when(txnBehavierService.create(eq(null), any(TxnCreateReq.class))).thenReturn(detail);

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(txnId.toString()))
                .andExpect(jsonPath("$.data.amount").value(100000));
    }

    @Test
    @DisplayName("GET /transactions/{id}: Lay chi tiet giao dich -> tra ve 200 OK")
    void get_Valid_ReturnsOk() throws Exception {
        UUID txnId = UUID.randomUUID();
        Instant now = Instant.now();
        TxnDetailResponse detail = new TxnDetailResponse(
                txnId,
                null,
                null,
                TxnType.INCOME,
                20_000_000L,
                now,
                "Lương",
                "Lương tháng",
                now,
                now
        );

        when(txnBehavierService.get(txnId, false)).thenReturn(detail);

        mockMvc.perform(get("/transactions/" + txnId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(txnId.toString()))
                .andExpect(jsonPath("$.data.amount").value(20000000));
    }

    @Test
    @DisplayName("PATCH /transactions/{id}: Cap nhat giao dich -> tra ve 200 OK")
    void update_Valid_ReturnsOk() throws Exception {
        UUID txnId = UUID.randomUUID();
        TxnUpdateReq req = new TxnUpdateReq(
                TxnType.EXPENSE,
                80_000L,
                UUID.randomUUID(),
                "cafe",
                Instant.now(),
                "Cafe",
                "Uống cafe"
        );

        mockMvc.perform(patch("/transactions/" + txnId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        verify(txnBehavierService).update(eq(txnId), any(TxnUpdateReq.class), eq(false));
    }

    @Test
    @DisplayName("DELETE /transactions/{id}: Xoa mem giao dich -> tra ve 200 OK")
    void delete_Valid_ReturnsOk() throws Exception {
        UUID txnId = UUID.randomUUID();

        mockMvc.perform(delete("/transactions/" + txnId))
                .andExpect(status().isOk());

        verify(txnBehavierService).softDelete(txnId, false);
    }
}

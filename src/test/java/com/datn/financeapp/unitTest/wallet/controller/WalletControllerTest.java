package com.datn.financeapp.unitTest.wallet.controller;

import com.datn.financeapp.common.exception.GlobalExceptionHandler;
import com.datn.financeapp.module.wallet.controller.WalletController;
import com.datn.financeapp.module.wallet.dto.request.WalletCreateReq;
import com.datn.financeapp.module.wallet.dto.request.WalletupdateReq;
import com.datn.financeapp.module.wallet.dto.response.WalletRes;
import com.datn.financeapp.module.wallet.enums.WalletType;
import com.datn.financeapp.module.wallet.service.WalletBehavierService;
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
class WalletControllerTest {

    @Mock
    private WalletBehavierService walletBehavierService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        mockMvc = MockMvcBuilders.standaloneSetup(new WalletController(walletBehavierService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    @DisplayName("POST /wallets: Chặn tạo thủ công ví loại SAVINGS -> trả về lỗi nghiệp vụ 400")
    void create_SavingsType_ThrowsForbiddenManualCreate() throws Exception {
        WalletCreateReq req = new WalletCreateReq(
                "Ví Tiết kiệm",
                WalletType.SAVINGS,
                1_000_000L,
                null,
                "icon_savings",
                "#2f8272"
        );

        mockMvc.perform(post("/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("WALLET_SAVINGS_MANUAL_CREATE_NOT_ALLOWED"));

        verify(walletBehavierService, never()).create(any(), anyBoolean());
    }

    @Test
    @DisplayName("POST /wallets: Tên ví rỗng -> trả về lỗi validation 400")
    void create_BlankName_ReturnsValidationError() throws Exception {
        WalletCreateReq req = new WalletCreateReq(
                "",
                WalletType.CASH,
                100_000L,
                null,
                "icon_cash",
                "#2f8272"
        );

        mockMvc.perform(post("/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        verify(walletBehavierService, never()).create(any(), anyBoolean());
    }

    @Test
    @DisplayName("POST /wallets: Số dư âm -> trả về lỗi validation 400")
    void create_NegativeBalance_ReturnsValidationError() throws Exception {
        WalletCreateReq req = new WalletCreateReq(
                "Ví Cá Nhân",
                WalletType.CASH,
                -50_000L,
                null,
                "icon_cash",
                "#2f8272"
        );

        mockMvc.perform(post("/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        verify(walletBehavierService, never()).create(any(), anyBoolean());
    }

    @Test
    @DisplayName("POST /wallets: Tạo ví hợp lệ -> trả về 201 Created")
    void create_Valid_ReturnsCreated() throws Exception {
        UUID walletId = UUID.randomUUID();
        WalletCreateReq req = new WalletCreateReq(
                "Ví Tiền Mặt",
                WalletType.CASH,
                500_000L,
                null,
                "icon_cash",
                "#2f8272"
        );
        WalletRes res = new WalletRes(
                walletId,
                "Ví Tiền Mặt",
                WalletType.CASH,
                500_000L,
                false,
                "icon_cash",
                "#2f8272",
                Instant.now()
        );

        when(walletBehavierService.create(any(WalletCreateReq.class), eq(false))).thenReturn(res);

        mockMvc.perform(post("/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(walletId.toString()))
                .andExpect(jsonPath("$.data.name").value("Ví Tiền Mặt"))
                .andExpect(jsonPath("$.data.balance").value(500000));
    }

    @Test
    @DisplayName("GET /wallets/{id}: Lấy chi tiết ví thành công -> trả về 200 OK")
    void get_Valid_ReturnsOk() throws Exception {
        UUID walletId = UUID.randomUUID();
        WalletRes res = new WalletRes(
                walletId,
                "Ví Ngân Hàng",
                WalletType.BANK,
                2_000_000L,
                true,
                "icon_bank",
                "#123456",
                Instant.now()
        );

        when(walletBehavierService.get(walletId, false)).thenReturn(res);

        mockMvc.perform(get("/wallets/" + walletId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Ví Ngân Hàng"))
                .andExpect(jsonPath("$.data.is_default").value(true));
    }

    @Test
    @DisplayName("PATCH /wallets/{id}: Cập nhật ví thành công -> trả về 200 OK")
    void update_Valid_ReturnsOk() throws Exception {
        UUID walletId = UUID.randomUUID();
        WalletupdateReq req = new WalletupdateReq("Tên mới", "icon_new", "#654321");
        WalletRes res = new WalletRes(
                walletId,
                "Tên mới",
                WalletType.CASH,
                1_000_000L,
                false,
                "icon_new",
                "#654321",
                Instant.now()
        );

        when(walletBehavierService.update(eq(walletId), any(WalletupdateReq.class), eq(false))).thenReturn(res);

        mockMvc.perform(patch("/wallets/" + walletId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Tên mới"));
    }

    @Test
    @DisplayName("DELETE /wallets/{id}: Xóa mềm ví -> trả về 200 OK")
    void delete_Valid_ReturnsOk() throws Exception {
        UUID walletId = UUID.randomUUID();

        mockMvc.perform(delete("/wallets/" + walletId))
                .andExpect(status().isOk());

        verify(walletBehavierService).softDelete(walletId, false);
    }
}

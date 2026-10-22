package com.datn.financeapp.unitTest.wallet;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.module.wallet.controller.WalletController;
import com.datn.financeapp.module.wallet.dto.request.WalletCreateReq;
import com.datn.financeapp.module.wallet.dto.response.WalletRes;
import com.datn.financeapp.module.wallet.enums.WalletType;
import com.datn.financeapp.module.wallet.service.WalletBehavierService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletControllerTest {

    @Mock
    private WalletBehavierService walletBehavierService;

    @InjectMocks
    private WalletController walletController;

    @Test
    @DisplayName("Chặn tạo ví loại SAVINGS trực tiếp qua endpoint POST /wallets")
    void createWallet_withSavingsType_throwsException() {
        WalletCreateReq req = new WalletCreateReq(
                "Ví tiết kiệm tự tạo",
                WalletType.SAVINGS,
                0L,
                null,
                "wallet",
                "#4CAF50"
        );

        BusinessException ex = assertThrows(BusinessException.class, () ->
                walletController.create(req));

        assertEquals(ErrorCode.WALLET_SAVINGS_MANUAL_CREATE_NOT_ALLOWED.getCode(), ex.getCode());
        verify(walletBehavierService, never()).create(any(), anyBoolean());
    }

    @Test
    @DisplayName("Cho phép tạo ví loại CASH thông thường")
    void createWallet_withCashType_delegatesToService() {
        WalletCreateReq req = new WalletCreateReq(
                "Ví tiền mặt",
                WalletType.CASH,
                500000L,
                null,
                "cash",
                "#4CAF50"
        );

        WalletRes expectedRes = new WalletRes(
                UUID.randomUUID(),
                "Ví tiền mặt",
                WalletType.CASH,
                500000L,
                false,
                "path/to/icon",
                "#4CAF50",
                Instant.now()
        );

        when(walletBehavierService.create(req, false)).thenReturn(expectedRes);

        ApiResponse<?> response = walletController.create(req);

        assertNotNull(response);
        assertEquals(expectedRes, response.data());
        verify(walletBehavierService).create(req, false);
    }
}

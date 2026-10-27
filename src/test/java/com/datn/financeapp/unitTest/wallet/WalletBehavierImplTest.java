package com.datn.financeapp.unitTest.wallet;

import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.wallet.entity.Wallet;
import com.datn.financeapp.module.wallet.enums.WalletType;
import com.datn.financeapp.module.wallet.repository.WalletRepository;
import com.datn.financeapp.module.wallet.service.impl.WalletBehavierImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletBehavierImplTest {

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private WalletBehavierImpl walletBehavierService;

    @Nested
    @DisplayName("softDelete - Xóa mềm ví")
    class SoftDeleteTests {

        @Test
        @DisplayName("Cho phép xóa ví CASH thông thường")
        void softDelete_cashWallet_success() {
            UUID walletId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            Wallet wallet = Wallet.builder()
                    .id(walletId)
                    .userId(userId)
                    .name("Tiền mặt phụ")
                    .type(WalletType.CASH)
                    .build();

            when(walletRepository.findByIdAndIsDeletedFalse(walletId)).thenReturn(Optional.of(wallet));

            try (MockedStatic<SecurityContextUtil> mockedSecurity = mockStatic(SecurityContextUtil.class)) {
                mockedSecurity.when(SecurityContextUtil::currentUserId).thenReturn(userId);

                assertDoesNotThrow(() -> walletBehavierService.softDelete(walletId, false));

                assertTrue(wallet.getIsDeleted());
                verify(walletRepository).save(wallet);
            }
        }
    }
}

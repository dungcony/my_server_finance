package com.datn.financeapp.unitTest.wallet;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.goal.repository.SavingsGoalRepository;
import com.datn.financeapp.module.icon.service.IconService;
import com.datn.financeapp.module.wallet.entity.Wallet;
import com.datn.financeapp.module.wallet.enums.WalletType;
import com.datn.financeapp.module.wallet.mapper.WalletMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletBehavierImplTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private SavingsGoalRepository savingsGoalRepository;

    @Mock
    private IconService iconService;

    @Mock
    private WalletMapper walletMapper;

    @InjectMocks
    private WalletBehavierImpl walletBehavierService;

    @Nested
    @DisplayName("Nghiệp vụ xóa mềm ví (softDelete)")
    class SoftDeleteTests {

        @Test
        @DisplayName("Chặn xóa ví SAVINGS khi đang liên kết với mục tiêu in_progress")
        void softDelete_savingsWalletLinkedToActiveGoal_throwsException() {
            UUID walletId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            Wallet wallet = Wallet.builder()
                    .id(walletId)
                    .userId(userId)
                    .name("Ví tiết kiệm mua xe")
                    .type(WalletType.SAVINGS)
                    .isDefault(false)
                    .build();

            when(walletRepository.findByIdAndIsDeletedFalse(walletId)).thenReturn(Optional.of(wallet));
            when(savingsGoalRepository.existsByWalletIdAndIsDeletedFalseAndStatus(walletId, "in_progress")).thenReturn(true);

            try (MockedStatic<SecurityContextUtil> mockedSecurity = mockStatic(SecurityContextUtil.class)) {
                mockedSecurity.when(SecurityContextUtil::currentUserId).thenReturn(userId);

                BusinessException ex = assertThrows(BusinessException.class, () ->
                        walletBehavierService.softDelete(walletId, false));

                assertEquals(ErrorCode.WALLET_SAVINGS_LINKED_TO_GOAL.getCode(), ex.getCode());
                verify(walletRepository, never()).save(any(Wallet.class));
            }
        }

        @Test
        @DisplayName("Cho phép xóa ví SAVINGS khi không có mục tiêu nào in_progress")
        void softDelete_savingsWalletNotLinkedToActiveGoal_success() {
            UUID walletId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            Wallet wallet = Wallet.builder()
                    .id(walletId)
                    .userId(userId)
                    .name("Ví tiết kiệm cũ")
                    .type(WalletType.SAVINGS)
                    .isDefault(false)
                    .build();

            when(walletRepository.findByIdAndIsDeletedFalse(walletId)).thenReturn(Optional.of(wallet));
            when(savingsGoalRepository.existsByWalletIdAndIsDeletedFalseAndStatus(walletId, "in_progress")).thenReturn(false);

            try (MockedStatic<SecurityContextUtil> mockedSecurity = mockStatic(SecurityContextUtil.class)) {
                mockedSecurity.when(SecurityContextUtil::currentUserId).thenReturn(userId);

                assertDoesNotThrow(() -> walletBehavierService.softDelete(walletId, false));

                assertTrue(wallet.getIsDeleted());
                verify(walletRepository).save(wallet);
            }
        }

        @Test
        @DisplayName("Cho phép xóa ví CASH thông thường nếu không phải mặc định")
        void softDelete_cashWallet_success() {
            UUID walletId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            Wallet wallet = Wallet.builder()
                    .id(walletId)
                    .userId(userId)
                    .name("Tiền mặt phụ")
                    .type(WalletType.CASH)
                    .isDefault(false)
                    .build();

            when(walletRepository.findByIdAndIsDeletedFalse(walletId)).thenReturn(Optional.of(wallet));

            try (MockedStatic<SecurityContextUtil> mockedSecurity = mockStatic(SecurityContextUtil.class)) {
                mockedSecurity.when(SecurityContextUtil::currentUserId).thenReturn(userId);

                assertDoesNotThrow(() -> walletBehavierService.softDelete(walletId, false));

                assertTrue(wallet.getIsDeleted());
                verify(savingsGoalRepository, never()).existsByWalletIdAndIsDeletedFalseAndStatus(any(), any());
                verify(walletRepository).save(wallet);
            }
        }

        @Test
        @DisplayName("Chặn xóa ví mặc định dù thuộc loại nào")
        void softDelete_defaultWallet_throwsException() {
            UUID walletId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            Wallet wallet = Wallet.builder()
                    .id(walletId)
                    .userId(userId)
                    .name("Ví mặc định")
                    .type(WalletType.CASH)
                    .isDefault(true)
                    .build();

            when(walletRepository.findByIdAndIsDeletedFalse(walletId)).thenReturn(Optional.of(wallet));

            try (MockedStatic<SecurityContextUtil> mockedSecurity = mockStatic(SecurityContextUtil.class)) {
                mockedSecurity.when(SecurityContextUtil::currentUserId).thenReturn(userId);

                BusinessException ex = assertThrows(BusinessException.class, () ->
                        walletBehavierService.softDelete(walletId, false));

                assertEquals(ErrorCode.WALLET_DEFAULT_NOT_DELETABLE.getCode(), ex.getCode());
            }
        }
    }
}

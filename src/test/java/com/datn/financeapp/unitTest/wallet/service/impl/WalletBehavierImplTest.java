package com.datn.financeapp.unitTest.wallet.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.goal.repository.SavingsGoalRepository;
import com.datn.financeapp.module.icon.dto.response.IconRes;
import com.datn.financeapp.module.icon.service.IconService;
import com.datn.financeapp.module.wallet.dto.request.WalletCreateReq;
import com.datn.financeapp.module.wallet.dto.request.WalletupdateReq;
import com.datn.financeapp.module.wallet.dto.response.WalletRes;
import com.datn.financeapp.module.wallet.entity.Wallet;
import com.datn.financeapp.module.wallet.enums.WalletType;
import com.datn.financeapp.module.wallet.mapper.WalletMapper;
import com.datn.financeapp.module.wallet.repository.WalletRepository;
import com.datn.financeapp.module.wallet.service.impl.WalletBehavierImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private WalletBehavierImpl walletBehavier;

    private MockedStatic<SecurityContextUtil> mockedSecurity;
    private final UUID currentUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockedSecurity = mockStatic(SecurityContextUtil.class);
        mockedSecurity.when(SecurityContextUtil::currentUserId).thenReturn(currentUserId);
    }

    @AfterEach
    void tearDown() {
        if (mockedSecurity != null) {
            mockedSecurity.close();
        }
    }

    @Test
    @DisplayName("create: Admin tao vi ma khong truyen userId -> nem VALIDATION_ERROR")
    void create_AdminMissingUserId_ThrowsException() {
        WalletCreateReq req = new WalletCreateReq("Vi Test", WalletType.CASH, 1000L, null, "icon_code", "#ffffff");
        Wallet wallet = Wallet.builder().name("Vi Test").iconCode("icon_code").build();
        when(walletMapper.toEntity(req)).thenReturn(wallet);

        assertThatThrownBy(() -> walletBehavier.create(req, true))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VALIDATION_ERROR.getCode());
    }

    @Test
    @DisplayName("create: Icon khong ton tai -> nem ICON_NOT_FOUND")
    void create_IconNotFound_ThrowsException() {
        WalletCreateReq req = new WalletCreateReq("Vi Test", WalletType.CASH, 1000L, currentUserId, "invalid_icon", "#ffffff");
        Wallet wallet = Wallet.builder().name("Vi Test").iconCode("invalid_icon").build();

        when(walletMapper.toEntity(req)).thenReturn(wallet);
        when(iconService.findByCode("invalid_icon", false)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletBehavier.create(req, false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ICON_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("create: Ten vi da ton tai -> nem WALLET_NAME_EXISTS")
    void create_NameExists_ThrowsException() {
        WalletCreateReq req = new WalletCreateReq("Vi Trung", WalletType.CASH, 1000L, currentUserId, "icon_wallet", "#ffffff");
        Wallet wallet = Wallet.builder().name("Vi Trung").iconCode("icon_wallet").build();

        when(walletMapper.toEntity(req)).thenReturn(wallet);
        when(iconService.findByCode("icon_wallet", false))
                .thenReturn(Optional.of(new IconRes("icon_wallet", "Tên icon", "path/data")));
        when(walletRepository.insertIfNameNotExist(any(Wallet.class))).thenReturn(0);

        assertThatThrownBy(() -> walletBehavier.create(req, false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.WALLET_NAME_EXISTS.getCode());
    }

    @Test
    @DisplayName("create: Tao vi hop le thanh cong")
    void create_Valid_Success() {
        WalletCreateReq req = new WalletCreateReq("Vi Moi", WalletType.CASH, 500_000L, currentUserId, "icon_wallet", "#112233");
        Wallet wallet = Wallet.builder().name("Vi Moi").iconCode("icon_wallet").build();
        WalletRes expectedRes = new WalletRes(UUID.randomUUID(), "Vi Moi", WalletType.CASH, 500_000L, false, "path/data", "#112233", Instant.now());

        when(walletMapper.toEntity(req)).thenReturn(wallet);
        when(iconService.findByCode("icon_wallet", false))
                .thenReturn(Optional.of(new IconRes("icon_wallet", "Tên icon", "path/data")));
        when(walletRepository.insertIfNameNotExist(any(Wallet.class))).thenReturn(1);
        when(walletMapper.toResponse(any(Wallet.class), eq("path/data"))).thenReturn(expectedRes);

        WalletRes res = walletBehavier.create(req, false);
        assertThat(res).isNotNull();
        assertThat(res.name()).isEqualTo("Vi Moi");
    }

    @Test
    @DisplayName("update: Khong tim thay vi -> nem WALLET_NOT_FOUND")
    void update_NotFound_ThrowsException() {
        UUID walletId = UUID.randomUUID();
        when(walletRepository.findByIdAndIsDeletedFalse(walletId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletBehavier.update(walletId, new WalletupdateReq("New Name", null, null), false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.WALLET_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("update: Khong phai chu so huu vi -> nem FORBIDDEN")
    void update_NotOwner_ThrowsForbidden() {
        UUID walletId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Wallet wallet = Wallet.builder().id(walletId).userId(otherUserId).name("Old Name").build();

        when(walletRepository.findByIdAndIsDeletedFalse(walletId)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> walletBehavier.update(walletId, new WalletupdateReq("New Name", null, null), false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.FORBIDDEN.getCode());
    }

    @Test
    @DisplayName("update: Doi ten trung voi vi khac cua user -> nem WALLET_NAME_EXISTS")
    void update_DuplicateName_ThrowsException() {
        UUID walletId = UUID.randomUUID();
        Wallet wallet = Wallet.builder().id(walletId).userId(currentUserId).name("Old Name").build();

        when(walletRepository.findByIdAndIsDeletedFalse(walletId)).thenReturn(Optional.of(wallet));
        when(walletRepository.existsByUserIdAndNameAndIsDeletedFalse(currentUserId, "Duplicated")).thenReturn(true);

        assertThatThrownBy(() -> walletBehavier.update(walletId, new WalletupdateReq("Duplicated", null, null), false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.WALLET_NAME_EXISTS.getCode());
    }

    @Test
    @DisplayName("softDelete: Chon xoa vi mac dinh -> nem WALLET_DEFAULT_NOT_DELETABLE")
    void softDelete_DefaultWallet_ThrowsException() {
        UUID walletId = UUID.randomUUID();
        Wallet wallet = Wallet.builder().id(walletId).userId(currentUserId).isDefault(true).build();

        when(walletRepository.findByIdAndIsDeletedFalse(walletId)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> walletBehavier.softDelete(walletId, false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.WALLET_DEFAULT_NOT_DELETABLE.getCode());
    }

    @Test
    @DisplayName("softDelete: Vi SAVINGS dang lien ket voi Goal chua hoan thanh -> nem WALLET_SAVINGS_LINKED_TO_GOAL")
    void softDelete_SavingsWithActiveGoal_ThrowsException() {
        UUID walletId = UUID.randomUUID();
        Wallet wallet = Wallet.builder().id(walletId).userId(currentUserId).type(WalletType.SAVINGS).isDefault(false).build();

        when(walletRepository.findByIdAndIsDeletedFalse(walletId)).thenReturn(Optional.of(wallet));
        when(savingsGoalRepository.existsByWalletIdAndIsDeletedFalseAndStatus(walletId, "in_progress")).thenReturn(true);

        assertThatThrownBy(() -> walletBehavier.softDelete(walletId, false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.WALLET_SAVINGS_LINKED_TO_GOAL.getCode());
    }

    @Test
    @DisplayName("softDelete: Xoa vi thanh cong danh dau isDeleted = true")
    void softDelete_Valid_Success() {
        UUID walletId = UUID.randomUUID();
        Wallet wallet = Wallet.builder().id(walletId).userId(currentUserId).type(WalletType.CASH).isDefault(false).build();

        when(walletRepository.findByIdAndIsDeletedFalse(walletId)).thenReturn(Optional.of(wallet));

        walletBehavier.softDelete(walletId, false);

        assertThat(wallet.getIsDeleted()).isTrue();
        verify(walletRepository).save(wallet);
    }
}

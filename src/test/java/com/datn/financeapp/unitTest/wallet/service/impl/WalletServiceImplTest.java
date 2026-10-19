package com.datn.financeapp.unitTest.wallet.service.impl;

import com.datn.financeapp.module.icon.service.IconService;
import com.datn.financeapp.module.wallet.dto.response.WalletSumaryRes;
import com.datn.financeapp.module.wallet.entity.Wallet;
import com.datn.financeapp.module.wallet.enums.WalletType;
import com.datn.financeapp.module.wallet.mapper.WalletMapper;
import com.datn.financeapp.module.wallet.repository.WalletRepository;
import com.datn.financeapp.module.wallet.service.impl.WalletServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceImplTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletMapper walletMapper;

    @Mock
    private IconService iconService;

    @InjectMocks
    private WalletServiceImpl walletService;

    @Test
    @DisplayName("findSumaryByIdAndUser: Tra ve rong neu khong tim thay vi")
    void findSumaryByIdAndUser_NotFound_ReturnsEmpty() {
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(walletRepository.findByIdAndUserId(walletId, userId)).thenReturn(Optional.empty());

        Optional<WalletSumaryRes> res = walletService.findSumaryByIdAndUser(walletId, userId, false);
        assertThat(res).isEmpty();
        verifyNoInteractions(walletMapper);
    }

    @Test
    @DisplayName("findSumaryByIdAndUser: Loc vi da bi xoa mem neu includeDeleted = false")
    void findSumaryByIdAndUser_Deleted_FilteredOut() {
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Wallet deletedWallet = Wallet.builder()
                .id(walletId)
                .userId(userId)
                .name("Old Wallet")
                .type(WalletType.CASH)
                .balance(0L)
                .build();
        deletedWallet.setIsDeleted(true);

        when(walletRepository.findByIdAndUserId(walletId, userId)).thenReturn(Optional.of(deletedWallet));

        Optional<WalletSumaryRes> res = walletService.findSumaryByIdAndUser(walletId, userId, false);
        assertThat(res).isEmpty();
    }

    @Test
    @DisplayName("findSumaryByIdAndUser: Tra ve vi da bi xoa mem neu includeDeleted = true")
    void findSumaryByIdAndUser_Deleted_Included() {
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Wallet deletedWallet = Wallet.builder()
                .id(walletId)
                .userId(userId)
                .name("Old Wallet")
                .iconCode("default_wallet")
                .type(WalletType.CASH)
                .balance(0L)
                .build();
        deletedWallet.setIsDeleted(true);

        WalletSumaryRes expectedRes = new WalletSumaryRes(walletId, "Old Wallet", WalletType.CASH, "path/to/icon", "#000000");

        when(walletRepository.findByIdAndUserId(walletId, userId)).thenReturn(Optional.of(deletedWallet));
        when(iconService.findIconByCode("default_wallet")).thenReturn("path/to/icon");
        when(walletMapper.toSumary(deletedWallet, "path/to/icon")).thenReturn(expectedRes);

        Optional<WalletSumaryRes> res = walletService.findSumaryByIdAndUser(walletId, userId, true);
        assertThat(res).isPresent();
        assertThat(res.get().id()).isEqualTo(walletId);
    }

    @Test
    @DisplayName("findSummaryMapByIds: Tra ve map rong khi tap id null hoac rong")
    void findSummaryMapByIds_NullOrEmpty_ReturnsEmptyMap() {
        assertThat(walletService.findSummaryMapByIds(null)).isEmpty();
        assertThat(walletService.findSummaryMapByIds(Collections.emptySet())).isEmpty();
        verifyNoInteractions(walletRepository);
    }

    @Test
    @DisplayName("findSummaryMapByIds: Query batch icon va map ket qua thanh cong")
    void findSummaryMapByIds_ValidIds_ReturnsMap() {
        UUID w1Id = UUID.randomUUID();
        UUID w2Id = UUID.randomUUID();

        Wallet w1 = Wallet.builder().id(w1Id).name("Wallet 1").iconCode("icon_cash").type(WalletType.CASH).build();
        Wallet w2 = Wallet.builder().id(w2Id).name("Wallet 2").iconCode("icon_bank").type(WalletType.BANK).build();

        WalletSumaryRes sum1 = new WalletSumaryRes(w1Id, "Wallet 1", WalletType.CASH, "path/cash", "#111111");
        WalletSumaryRes sum2 = new WalletSumaryRes(w2Id, "Wallet 2", WalletType.BANK, "path/bank", "#222222");

        when(walletRepository.findAllById(Set.of(w1Id, w2Id))).thenReturn(List.of(w1, w2));
        when(iconService.findIconsByCodes(List.of("icon_cash", "icon_bank"))).thenReturn(Map.of(
                "icon_cash", "path/cash",
                "icon_bank", "path/bank"
        ));
        when(walletMapper.toSumary(w1, "path/cash")).thenReturn(sum1);
        when(walletMapper.toSumary(w2, "path/bank")).thenReturn(sum2);

        Map<UUID, WalletSumaryRes> map = walletService.findSummaryMapByIds(Set.of(w1Id, w2Id));

        assertThat(map).hasSize(2);
        assertThat(map.get(w1Id)).isEqualTo(sum1);
        assertThat(map.get(w2Id)).isEqualTo(sum2);
    }
}

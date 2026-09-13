package com.datn.financeapp.wallet.service;

import com.datn.financeapp.wallet.dto.request.CreateWalletRequest;
import com.datn.financeapp.wallet.dto.request.ReorderWalletsRequest;
import com.datn.financeapp.wallet.dto.request.UpdateWalletRequest;
import com.datn.financeapp.wallet.dto.response.WalletDetailResponse;
import com.datn.financeapp.wallet.dto.response.WalletRawBalanceResponse;
import com.datn.financeapp.wallet.dto.response.WalletRefResponse;
import com.datn.financeapp.wallet.dto.response.WalletResponse;
import com.datn.financeapp.wallet.dto.response.WalletSummaryResponse;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Public API của module Wallet (CRUD và tra cứu thông tin ví).
public interface WalletService {

    List<WalletResponse> list(UUID userId, String type, Boolean onlyInTotal, boolean includeShared);

    WalletSummaryResponse summary(UUID userId);

    WalletDetailResponse detail(UUID userId, UUID walletId);

    WalletResponse create(UUID userId, CreateWalletRequest req);

    void createDefaultCashWallet(UUID userId, Instant createdAt);

    WalletRefResponse findRefForUser(UUID userId, UUID walletId);

    List<WalletRawBalanceResponse> listWithRawBalance(UUID userId, boolean includeShared);

    Map<UUID, WalletRefResponse> findRefsForUser(Collection<UUID> walletIds, UUID userId);

    long requireRawBalance(UUID walletId);

    void lockWalletsInOrder(Set<UUID> walletIds, UUID userId);

    void adjustBalance(UUID walletId, long delta);

    Long findRawBalance(UUID walletId);

    WalletRefResponse findRefById(UUID walletId);

    long countActiveWallets(UUID userId);

    WalletResponse update(UUID userId, UUID walletId, UpdateWalletRequest req);

    void delete(UUID userId, UUID walletId, boolean deleteTransactions);

    void reorder(UUID userId, ReorderWalletsRequest req);
}

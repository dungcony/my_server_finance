package com.datn.financeapp.wallet.service;

import com.datn.financeapp.wallet.dto.request.AdjustBalanceRequest;
import com.datn.financeapp.wallet.dto.request.TransferRequest;
import com.datn.financeapp.wallet.dto.response.AdjustBalanceResponse;
import com.datn.financeapp.wallet.dto.response.ReconcileResponse;
import com.datn.financeapp.wallet.dto.response.TransferResponse;
import java.util.UUID;

// Public API của module Wallet cho các tác vụ chuyển tiền, điều chỉnh số dư và đối chiếu.
public interface WalletTransferService {

    TransferResponse transfer(UUID userId, TransferRequest req);

    AdjustBalanceResponse adjustBalance(UUID userId, UUID walletId, AdjustBalanceRequest req);

    ReconcileResponse reconcile(UUID userId, UUID walletId, boolean autoFix);

    void reconcileAllWallets();
}

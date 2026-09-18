package com.datn.financeapp.group.service;

import com.datn.financeapp.group.dto.wallet.GroupWalletCreateReq;
import com.datn.financeapp.group.dto.wallet.GroupWalletReconcileReq;
import com.datn.financeapp.group.dto.wallet.GroupWalletReconcileRes;
import com.datn.financeapp.group.dto.wallet.GroupWalletRes;
import com.datn.financeapp.group.dto.wallet.GroupWalletUpdateReq;
import java.util.List;
import java.util.UUID;

public interface GroupWalletService {

    GroupWalletRes getFund(UUID userId, UUID groupId);

    GroupWalletRes updateFund(UUID userId, UUID groupId, GroupWalletUpdateReq req);

    GroupWalletReconcileRes reconcileFund(UUID userId, UUID groupId, GroupWalletReconcileReq req);

    void adjustBalance(UUID walletId, Long delta);

    // Backward compatibility methods
    GroupWalletRes create(UUID userId, UUID groupId, GroupWalletCreateReq req);

    List<GroupWalletRes> list(UUID userId, UUID groupId);

    GroupWalletRes detail(UUID userId, UUID groupId, UUID walletId);

    GroupWalletRes update(UUID userId, UUID groupId, UUID walletId, GroupWalletUpdateReq req);

    GroupWalletReconcileRes reconcile(UUID userId, UUID groupId, UUID walletId, GroupWalletReconcileReq req);
}

package com.datn.financeapp.module.wallet.service.impl;

import com.datn.financeapp.common.response.PageMeta;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.wallet.dto.response.ReconcilListRes;
import com.datn.financeapp.module.wallet.dto.response.ReconcileRes;
import com.datn.financeapp.module.wallet.dto.response.WalletSumaryRes;
import com.datn.financeapp.module.wallet.entity.ReconcilHistory;
import com.datn.financeapp.module.wallet.repository.ReconcilHistoryRepository;
import com.datn.financeapp.module.wallet.service.ReconcilHistoryService;
import com.datn.financeapp.module.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReconcilHistoryImpl implements ReconcilHistoryService {
    private final ReconcilHistoryRepository reconcilHistoryRepository;
    private final WalletService walletService;

    @Override
    public ReconcilListRes gets(Pageable pageable) {
        Page<ReconcilHistory> reconcils = reconcilHistoryRepository.findAll(pageable);

        return toListRes(reconcils, walletService::findSumary);
    }

    @Override
    public ReconcilListRes getsByWallet(UUID userId, UUID walletId, Pageable pageable) {
        if (userId == null)
            userId = SecurityContextUtil.currentUserId();

        // kiểm tra ví thuộc người dùng, ví của người khác báo không tìm thấy
        WalletSumaryRes wallet = walletService.findSumary(walletId, userId);

        Page<ReconcilHistory> reconcils = reconcilHistoryRepository.findAllByWalletId(walletId, pageable);

        return toListRes(reconcils, id -> wallet);
    }

    //-------------------------------------PRIVATE-------------------------------//

    private ReconcilListRes toListRes(Page<ReconcilHistory> reconcils, Function<UUID, WalletSumaryRes> walletOf) {
        List<ReconcileRes> items = reconcils.getContent().stream()
                .map(r -> new ReconcileRes(
                        r.getId(),
                        walletOf.apply(r.getWalletId()),
                        r.getWalletBalance(),
                        r.getToltalTxn(),
                        r.getCreatedAt()
                ))
                .toList();

        return new ReconcilListRes(items, PageMeta.from(reconcils));
    }
}

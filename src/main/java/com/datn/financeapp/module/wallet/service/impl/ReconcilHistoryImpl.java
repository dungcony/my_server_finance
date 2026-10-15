package com.datn.financeapp.module.wallet.service.impl;

import com.datn.financeapp.common.response.PageMeta;
import com.datn.financeapp.module.transaction.dto.response.ReconcilListRes;
import com.datn.financeapp.module.wallet.dto.response.ReconcileRes;
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

        List<ReconcileRes> items = reconcils.getContent().stream()
                .map(r -> new ReconcileRes(
                        walletService.findSumary(r.getWalletId()),
                        r.getToltalTxn(),
                        r.getWalletBalance()
                ))
                .toList();

        return new ReconcilListRes(items, PageMeta.from(reconcils));
    }
}

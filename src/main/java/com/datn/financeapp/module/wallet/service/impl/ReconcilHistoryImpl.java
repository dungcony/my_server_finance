package com.datn.financeapp.module.wallet.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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

        // Gom tất cả walletId duy nhất trong page
        Set<UUID> walletIds = reconcils.getContent().stream()
                .map(ReconcilHistory::getWalletId)
                .collect(Collectors.toSet());

        // Query 1 lần ra Map<UUID, WalletSumaryRes>
        Map<UUID, WalletSumaryRes> walletMap = walletService.findSummaryMapByIds(walletIds);

        // walletMap::get chính là một Function<UUID, WalletSumaryRes>
        return toListRes(reconcils, walletMap);
    }

    @Override
    public ReconcilListRes getsByWallet(UUID userId, UUID walletId, Pageable pageable) {
        if (userId == null)
            userId = SecurityContextUtil.currentUserId();

        // kiểm tra ví thuộc người dùng, ví của người khác báo không tìm thấy
        WalletSumaryRes wallet = walletService.findSumaryByIdAndUser(walletId, userId, true)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "ví đã bị xoá hoặc k phải của user này"));

        Page<ReconcilHistory> reconcils = reconcilHistoryRepository.findAllByWalletId(walletId, pageable);

        return toListRes(reconcils, Map.of(walletId, wallet));
    }

    //-------------------------------------PRIVATE-------------------------------//
    private ReconcilListRes toListRes(Page<ReconcilHistory> reconcils, Map<UUID, WalletSumaryRes> walletMap) {
        List<ReconcileRes> items = reconcils.getContent().stream()
                .map(r -> new ReconcileRes(
                        r.getId(),
                        walletMap.get(r.getWalletId()), // Lấy trực tiếp từ Map, ai đọc cũng hiểu ngay
                        r.getWalletBalance(),
                        r.getToltalTxn(),
                        r.getCreatedAt()
                ))
                .toList();

        return new ReconcilListRes(items, PageMeta.from(reconcils));
    }
}

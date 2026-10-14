package com.datn.financeapp.module.wallet.service.impl;

import com.datn.financeapp.module.icon.service.IconService;
import com.datn.financeapp.module.wallet.service.WalletService;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.wallet.dto.response.WalletRes;
import com.datn.financeapp.module.wallet.entity.Wallet;
import com.datn.financeapp.module.wallet.mapper.WalletMapper;
import com.datn.financeapp.module.wallet.repository.WalletRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Set;
import java.util.Collection;
import java.util.HashMap;
import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {
    private final WalletRepository walletRepository;
    private final WalletMapper walletMapper;

    private final IconService iconService;


    @Override
    @Transactional(readOnly = true)
    public WalletRes findDefaultWallet(UUID userId) {
        return walletRepository.findByUserIdAndIsDefaultTrue(userId)
                .map(w -> walletMapper.toResponse(w, ""))
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_DEFAULT_NOT_FOUND));
    }

    @Override
    public boolean myWallet(UUID userId, UUID walletId) {
        return walletRepository.findByUserIdAndIsDefaultTrue(userId).isPresent();
    }


    @Override
    @Transactional(readOnly = true)
    public WalletSumaryRes findRefForUser(UUID userId, UUID walletId) {
        if (walletId == null) {
            return null;
        }
        return walletRepository
                .findByIdForUser(walletId, userId)
                .map(walletMapper::toRef)
                .orElse(null);
    }


    @Override
    @Transactional(readOnly = true)
    public List<WalletRawBalanceResponse> listWithRawBalance(UUID userId, boolean includeShared) {
        return walletRepository.findAllForUser(userId, null, null, includeShared).stream()
                .map(w -> new WalletRawBalanceResponse(
                        w.getId(), w.getName(), w.getType(), w.getCurrentBalance()))
                .toList();
    }


    @Override
    @Transactional(readOnly = true)
    public Map<UUID, WalletSumaryRes> findRefsForUser(Collection<UUID> walletIds, UUID userId) {
        Map<UUID, WalletSumaryRes> byId = new HashMap<>();
        for (UUID id : walletIds.stream().filter(Objects::nonNull).distinct().toList()) {
            WalletSumaryRes ref = findRefForUser(userId, id);
            if (ref != null) {
                byId.put(id, ref);
            }
        }
        return byId;
    }


    @Override
    @Transactional(readOnly = true)
    public long requireRawBalance(UUID walletId) {
        return walletRepository.findCurrentBalanceNative(walletId).orElseThrow();
    }


    @Override
    @Transactional
    public void lockWalletsInOrder(Set<UUID> walletIds, UUID userId) {
        List<UUID> sortedIds = new ArrayList<>(walletIds);
        sortedIds.sort(UUID::compareTo);
        for (UUID walletId : sortedIds) {
            Wallet wallet = walletRepository
                    .findByIdForUpdate(walletId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy ví."));
            if (!userId.equals(wallet.getUserId())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy ví.");
            }
        }
    }


    @Override
    @Transactional
    public void adjustBalance(UUID walletId, long delta) {
        walletRepository.adjustBalance(walletId, delta);
    }


    @Override
    @Transactional(readOnly = true)
    public Long findRawBalance(UUID walletId) {
        if (walletId == null) {
            return null;
        }
        return walletRepository.findCurrentBalanceNative(walletId).orElse(null);
    }


    @Override
    @Transactional(readOnly = true)
    public WalletSumaryRes findRefById(UUID walletId) {
        if (walletId == null) {
            return null;
        }
        return walletRepository
                .findById(walletId)
                .map(walletMapper::toRef)
                .orElse(null);
    }


    @Override
    @Transactional(readOnly = true)
    public long countActiveWallets(UUID userId) {
        return walletRepository.countByUserIdAndIsDeletedFalse(userId);
    }

    // đặt ví này làm mặc định thì bỏ cờ của ví cũ trước; bỏ cờ mặc định trực tiếp không được phép
    private void applyDefaultChange(UUID userId, Wallet wallet, boolean makeDefault) {
        boolean alreadyDefault = Boolean.TRUE.equals(wallet.getIsDefault());
        if (makeDefault && !alreadyDefault) {
            walletRepository.clearDefault(userId);
            wallet.setIsDefault(true);
        } else if (!makeDefault && alreadyDefault) {
            throw new BusinessException(ErrorCode.WALLET_DEFAULT_REQUIRED);
        }
    }

    private WalletStatsResponse loadStats(UUID walletId) {
        Long transactionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE wallet_id = ? AND NOT is_deleted",
                Long.class, walletId);
        Long incomeThisMonth = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE wallet_id = ? AND type = 'INCOME' "
                        + "AND NOT is_deleted AND date_trunc('month', date) = date_trunc('month', CURRENT_DATE)",
                Long.class, walletId);
        Long expenseThisMonth = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE wallet_id = ? AND type IN ('EXPENSE', 'LOAN') "
                        + "AND NOT is_deleted AND date_trunc('month', date) = date_trunc('month', CURRENT_DATE)",
                Long.class, walletId);
        LocalDate lastTransactionDate = jdbcTemplate.query(
                "SELECT MAX(date) FROM transactions WHERE wallet_id = ? AND NOT is_deleted",
                rs -> rs.next() ? rs.getObject(1, LocalDate.class) : null,
                walletId);

        return new WalletStatsResponse(
                transactionCount == null ? 0 : transactionCount,
                incomeThisMonth == null ? 0 : incomeThisMonth,
                expenseThisMonth == null ? 0 : expenseThisMonth,
                lastTransactionDate);
    }

    private WalletRes toResponse(Wallet wallet) {
        // D-37/TXN-09: cùng logic trừ ngược như detail() — currentBalance API luôn là tiền thật
        // đến hết hôm nay, projectedBalance chỉ khác NULL khi ví có giao dịch tương lai.
        long currentBalanceAsOfToday = walletRepository
                .findBalanceAsOf(wallet.getId(), LocalDate.now())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy ví."));
        boolean hasFuture = walletRepository.hasFutureTransactions(wallet.getId());
        Long projectedBalance = hasFuture ? wallet.getCurrentBalance() : null;

        return walletMapper.toResponse(wallet, currentBalanceAsOfToday, projectedBalance);
    }
}

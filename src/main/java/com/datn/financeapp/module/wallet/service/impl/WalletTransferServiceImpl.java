package com.datn.financeapp.module.wallet.service.impl;

import com.datn.financeapp.module.wallet.service.WalletTransferService;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.wallet.dto.response.ReconcileResponse;
import com.datn.financeapp.module.wallet.entity.Wallet;
import com.datn.financeapp.module.wallet.repository.WalletRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đối chiếu số dư ví (WALLET-07) — api/02-VI.md mục 10. Tách riêng khỏi {@link WalletService} vì
 * nghiệp vụ thao tác trực tiếp lên {@code current_balance} qua atomic UPDATE, khác nhóm CRUD thuần.
 * <p>
 * {@code @Transactional} đặt TRÊN TỪNG public method — tránh gọi chéo giữa các method
 * {@code @Transactional} trong cùng class (self-invocation làm mất proxy AOP, xem
 * {@code IdempotencyTransactionHelper} Phase 1).
 */
@Service
@RequiredArgsConstructor
public class WalletTransferServiceImpl implements WalletTransferService {

    private static final Logger log = LoggerFactory.getLogger(WalletTransferService.class);

    private final WalletRepository walletRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ReconciliationWorker reconciliationWorker;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public ReconcileResponse reconcile(UUID userId, UUID walletId, boolean autoFix) {
        Wallet wallet = walletRepository
                .findByIdForUser(walletId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy ví."));

        return doReconcile(wallet, autoFix);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reconcileAllWallets() {
        List<UUID> allWalletIds = walletRepository.findAllActiveWalletIds();
        for (UUID walletId : allWalletIds) {
            try {
                reconciliationWorker.reconcileOneWalletAutoFix(walletId);
            } catch (Exception e) {
                log.error("Lỗi khi đối chiếu ví {}", walletId, e);
            }
        }
    }

    // Công thức đối chiếu dùng chung cho cả endpoint thủ công (WALLET-07) và job hệ thống (JOB-01).
    private ReconcileResponse doReconcile(Wallet wallet, boolean autoFix) {
        return reconciliationWorker.doReconcile(wallet, autoFix);
    }

    /**
     * Bean riêng chỉ để {@code reconcileOneWalletAutoFix} đi qua đúng Spring AOP proxy cho
     * {@code @Transactional} khi được gọi từ vòng lặp {@link #reconcileAllWallets} — gọi trực
     * tiếp method {@code @Transactional} khác trong CÙNG class ({@code this.xxx()}) là self-
     * invocation, bỏ qua proxy và làm mất transaction hoàn toàn (bài học
     * {@code IdempotencyTransactionHelper}, Phase 1).
     */
    @Component
    @RequiredArgsConstructor
    static class ReconciliationWorker {

        private static final Logger workerLog = LoggerFactory.getLogger(ReconciliationWorker.class);

        private final WalletRepository walletRepository;
        private final JdbcTemplate jdbcTemplate;

        /**
         * Đối chiếu MỘT ví cho tác vụ nền — job hệ thống không có "người dùng đang gọi" nên đọc
         * {@link Wallet} trực tiếp qua {@code walletRepository.findById}, bỏ qua bước kiểm tra
         * quyền D-27 mà {@code reconcile()} áp dụng cho request người dùng.
         */
        @Transactional
        void reconcileOneWalletAutoFix(UUID walletId) {
            Wallet wallet = walletRepository
                    .findById(walletId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy ví."));
            doReconcile(wallet, true);
        }

        ReconcileResponse doReconcile(Wallet wallet, boolean autoFix) {
            UUID walletId = wallet.getId();
            Long computedBalance = jdbcTemplate.queryForObject(
                    "SELECT w.initial_balance "
                            + "  + COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount END), 0) "
                            + "  - COALESCE(SUM(CASE WHEN t.type IN ('EXPENSE', 'LOAN') THEN t.amount END), 0) "
                            + "FROM wallets w "
                            + "LEFT JOIN transactions t ON t.wallet_id = w.id AND NOT t.is_deleted "
                            + "WHERE w.id = ? "
                            + "GROUP BY w.id, w.initial_balance",
                    Long.class, walletId);
            if (computedBalance == null) {
                computedBalance = wallet.getInitialBalance();
            }

            long storedBalance = wallet.getCurrentBalance();
            long difference = storedBalance - computedBalance;
            boolean wasFixed = false;

            if (difference != 0) {
                workerLog.warn(
                        "Lệch số dư ví phát hiện khi reconcile: walletId={}, storedBalance={}, "
                                + "computedBalance={}, difference={}, timestamp={}",
                        walletId, storedBalance, computedBalance, difference, Instant.now());

                if (autoFix) {
                    walletRepository.adjustBalance(walletId, -difference);
                    wasFixed = true;
                }
            }

            return new ReconcileResponse(
                    walletId, storedBalance, computedBalance, difference, difference == 0, wasFixed);
        }
    }
}

package com.datn.financeapp.module.wallet.service.impl;

import com.datn.financeapp.module.wallet.service.WalletTransferService;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.transaction.service.TransactionWriteCommand;
import com.datn.financeapp.module.transaction.service.TransactionWriter;
import com.datn.financeapp.module.wallet.dto.request.AdjustBalanceRequest;
import com.datn.financeapp.module.wallet.dto.response.AdjustBalanceResponse;
import com.datn.financeapp.module.wallet.dto.response.ReconcileResponse;
import com.datn.financeapp.module.wallet.dto.request.TransferRequest;
import com.datn.financeapp.module.wallet.dto.response.TransferResponse;
import com.datn.financeapp.module.wallet.entity.Wallet;
import com.datn.financeapp.module.wallet.repository.WalletRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chuyển tiền (WALLET-06), điều chỉnh số dư kiểm kê (WALLET-08) và đối chiếu (WALLET-07) —
 * api/02-VI.md mục 8-10. Tách riêng khỏi {@link WalletService} vì cả ba nghiệp vụ đều thao tác
 * trực tiếp lên {@code current_balance} qua atomic UPDATE + lock, khác nhóm CRUD thuần.
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
    private final TransactionWriter transactionWriter;
    private final ReconciliationWorker reconciliationWorker;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public TransferResponse transfer(UUID userId, TransferRequest req) {
        UUID sourceId = req.sourceWalletId();
        UUID destinationId = req.destinationWalletId();

        if (sourceId.equals(destinationId)) {
            throw new BusinessException(ErrorCode.WALLET_TRANSFER_SAME_DESTINATION);
        }

        // Khoá theo thứ tự cố định (nhỏ trước, lớn sau) bất kể vai trò nguồn/đích — chống
        // deadlock khi hai giao dịch A->B và B->A chạy đồng thời (T-02-12).
        UUID first = sourceId.compareTo(destinationId) < 0 ? sourceId : destinationId;
        UUID second = sourceId.compareTo(destinationId) < 0 ? destinationId : sourceId;

        Wallet firstWallet = lockAndCheckOwnership(first, userId);
        Wallet secondWallet = lockAndCheckOwnership(second, userId);

        Wallet sourceWallet = sourceId.equals(first) ? firstWallet : secondWallet;
        Wallet destinationWallet = destinationId.equals(first) ? firstWallet : secondWallet;

        long amount = req.amount();
        if (Boolean.TRUE.equals(req.failIfInsufficient()) && sourceWallet.getCurrentBalance() < amount) {
            throw new BusinessException(ErrorCode.WALLET_BALANCE_INSUFFICIENT, "Số dư ví không đủ để thực hiện giao dịch này.", Map.of(
                    "current_balance", sourceWallet.getCurrentBalance(),
                    "requested_amount", amount));
        }

        LocalDate date = req.date() != null ? req.date() : LocalDate.now();

        TransactionWriter.WriteResult result = transactionWriter.write(new TransactionWriteCommand(
                UUID.randomUUID(), userId, sourceId, destinationId, null, "transfer", amount, date,
                req.note(), null, "manual", true, null, null, null));

        return new TransferResponse(
                new TransferResponse.TransactionInfo(
                        result.transactionId(),
                        "transfer",
                        amount,
                        date,
                        new TransferResponse.WalletRef(sourceWallet.getId(), sourceWallet.getName()),
                        new TransferResponse.WalletRef(destinationWallet.getId(), destinationWallet.getName()),
                        req.note()),
                new TransferResponse.NewBalance(result.walletNewBalance(), result.destinationWalletNewBalance()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public AdjustBalanceResponse adjustBalance(UUID userId, UUID walletId, AdjustBalanceRequest req) {
        Wallet wallet = lockAndCheckOwnership(walletId, userId);

        if (req.actualBalance() < 0) {
            throw new BusinessException(ErrorCode.TRANSACTION_AMOUNT_INVALID, "Số dư thực tế không được âm.");
        }

        long previousBalance = wallet.getCurrentBalance();
        long difference = req.actualBalance() - previousBalance;

        // ck_txn_amount CHECK (amount > 0) chặn amount=0 ở tầng DB, nhưng phải xử lý tường minh
        // ở Service TRƯỚC khi build câu SQL — không dựa vào DB reject.
        if (difference == 0) {
            return new AdjustBalanceResponse(
                    walletId, previousBalance, req.actualBalance(), 0L, false, null, null, null);
        }

        String type = difference < 0 ? "expense" : "income";
        long amount = Math.abs(difference);
        boolean countsInReport = req.countsInReport() == null ? true : req.countsInReport();

        UUID categoryId = jdbcTemplate.queryForObject(
                "SELECT c.id FROM categories c "
                        + "JOIN category_groups g ON g.id = c.category_group_id "
                        + "JOIN icons i ON i.id = c.icon_id "
                        + "WHERE g.name = 'Khác' AND i.code = 'vi_tien' AND c.type = ? AND c.user_id IS NULL",
                UUID.class, type);
        if (categoryId == null) {
            throw new IllegalStateException(
                    "Thiếu danh mục hệ thống 'Cập nhật số dư' (type=" + type + ") — kiểm tra seed V8.");
        }

        TransactionWriter.WriteResult result = transactionWriter.write(new TransactionWriteCommand(
                UUID.randomUUID(), userId, walletId, null, categoryId, type, amount, LocalDate.now(),
                req.note(), null, "adjustment", countsInReport, null, null, null));

        return new AdjustBalanceResponse(
                walletId,
                previousBalance,
                req.actualBalance(),
                difference,
                true,
                result.transactionId(),
                type,
                countsInReport);
    }

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
                            + "  + COALESCE(SUM(CASE WHEN t.type='income'  THEN t.amount END), 0) "
                            + "  - COALESCE(SUM(CASE WHEN t.type='expense' THEN t.amount END), 0) "
                            + "  - COALESCE(SUM(CASE WHEN t.type='transfer' AND t.wallet_id = w.id THEN t.amount END), 0) "
                            + "  + COALESCE(SUM(CASE WHEN t.type='transfer' AND t.destination_wallet_id = w.id THEN t.amount END), 0) "
                            + "FROM wallets w "
                            + "LEFT JOIN transactions t ON (t.wallet_id = w.id OR t.destination_wallet_id = w.id) AND NOT t.is_deleted "
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

    /**
     * Lock ví theo id rồi kiểm tra quyền D-27 (Phase 2 chỉ có nhánh cá nhân, nhóm mãi Phase 5).
     * Không tồn tại hoặc không thuộc quyền -> NOT_FOUND 404, không tiết lộ ví có tồn tại hay
     * không (T-02-13).
     */
    private Wallet lockAndCheckOwnership(UUID walletId, UUID userId) {
        Wallet wallet = walletRepository
                .findByIdForUpdate(walletId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy ví."));
        if (!userId.equals(wallet.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy ví.");
        }
        return wallet;
    }
}

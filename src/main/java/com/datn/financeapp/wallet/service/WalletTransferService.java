package com.datn.financeapp.wallet.service;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.wallet.dto.AdjustBalanceRequest;
import com.datn.financeapp.wallet.dto.AdjustBalanceResponse;
import com.datn.financeapp.wallet.dto.ReconcileResponse;
import com.datn.financeapp.wallet.dto.TransferRequest;
import com.datn.financeapp.wallet.dto.TransferResponse;
import com.datn.financeapp.wallet.entity.Wallet;
import com.datn.financeapp.wallet.repository.WalletRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chuyển tiền (WALLET-06), điều chỉnh số dư kiểm kê (WALLET-08) và đối chiếu (WALLET-07) —
 * api/02-VI.md mục 8-10. Tách riêng khỏi {@link WalletService} vì cả ba nghiệp vụ đều thao tác
 * trực tiếp lên {@code current_balance} qua atomic UPDATE + lock, khác nhóm CRUD thuần.
 *
 * {@code @Transactional} đặt TRÊN TỪNG public method — tránh gọi chéo giữa các method
 * {@code @Transactional} trong cùng class (self-invocation làm mất proxy AOP, xem
 * {@code IdempotencyTransactionHelper} Phase 1).
 */
@Service
@RequiredArgsConstructor
public class WalletTransferService {

    private static final Logger log = LoggerFactory.getLogger(WalletTransferService.class);

    private final WalletRepository walletRepository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * api/02-VI.md mục 8. Khoá 2 ví theo thứ tự {@code UUID.compareTo()} cố định (T-02-12) rồi
     * atomic UPDATE số dư — không load-modify-save (T-02-11).
     */
    @Transactional
    public TransferResponse transfer(UUID userId, TransferRequest req) {
        UUID sourceId = req.sourceWalletId();
        UUID destinationId = req.destinationWalletId();

        if (sourceId.equals(destinationId)) {
            throw new BusinessException(
                    "SAME_SOURCE_AND_DESTINATION", HttpStatus.BAD_REQUEST.value(), "Hai ví phải khác nhau.");
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
            throw new BusinessException(
                    "INSUFFICIENT_BALANCE",
                    HttpStatus.UNPROCESSABLE_ENTITY.value(),
                    "Số dư ví không đủ để thực hiện giao dịch này.",
                    Map.of(
                            "current_balance", sourceWallet.getCurrentBalance(),
                            "requested_amount", amount));
        }

        LocalDate date = req.date() != null ? req.date() : LocalDate.now();
        UUID transactionId = UUID.randomUUID();

        jdbcTemplate.update(
                "INSERT INTO transactions (id, user_id, wallet_id, destination_wallet_id, category_id, "
                        + "type, amount, date, note, source, counts_in_report, is_deleted, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, NULL, 'transfer', ?, ?, ?, 'manual', TRUE, FALSE, now(), now())",
                transactionId, userId, sourceId, destinationId, amount, date, req.note());

        walletRepository.adjustBalance(sourceId, -amount);
        walletRepository.adjustBalance(destinationId, amount);

        long newSourceBalance = sourceWallet.getCurrentBalance() - amount;
        long newDestinationBalance = destinationWallet.getCurrentBalance() + amount;

        return new TransferResponse(
                new TransferResponse.TransactionInfo(
                        transactionId,
                        "transfer",
                        amount,
                        date,
                        new TransferResponse.WalletRef(sourceWallet.getId(), sourceWallet.getName()),
                        new TransferResponse.WalletRef(destinationWallet.getId(), destinationWallet.getName()),
                        req.note()),
                new TransferResponse.NewBalance(newSourceBalance, newDestinationBalance));
    }

    /**
     * api/02-VI.md mục 9 (WALLET-08). Người dùng chủ động kiểm kê — lệch là bình thường, do
     * quên ghi. KHÔNG ghi đè {@code current_balance}, sinh một giao dịch bù đúng phần chênh
     * (source='adjustment', danh mục hệ thống "Cập nhật số dư" — V8). Đừng nhầm với
     * {@link #reconcile}.
     */
    @Transactional
    public AdjustBalanceResponse adjustBalance(UUID userId, UUID walletId, AdjustBalanceRequest req) {
        Wallet wallet = lockAndCheckOwnership(walletId, userId);

        if (req.actualBalance() < 0) {
            throw new BusinessException(
                    "INVALID_AMOUNT", HttpStatus.BAD_REQUEST.value(), "Số dư thực tế không được âm.");
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

        UUID transactionId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO transactions (id, user_id, wallet_id, destination_wallet_id, category_id, "
                        + "type, amount, date, note, source, counts_in_report, is_deleted, created_at, updated_at) "
                        + "VALUES (?, ?, ?, NULL, ?, ?, ?, CURRENT_DATE, ?, 'adjustment', ?, FALSE, now(), now())",
                transactionId, userId, walletId, categoryId, type, amount, req.note(), countsInReport);

        walletRepository.adjustBalance(walletId, difference);

        return new AdjustBalanceResponse(
                walletId, previousBalance, req.actualBalance(), difference, true, transactionId, type, countsInReport);
    }

    /**
     * api/02-VI.md mục 10 (WALLET-07). Máy tự dò lỗi hệ thống — lệch nghĩa là backend có bug.
     * KHÔNG tự động sửa số trừ khi {@code autoFix=true}; luôn ghi log khi phát hiện lệch
     * (T-02-15). D-25: chỉ giữ endpoint thủ công, không có job {@code @Scheduled}.
     */
    @Transactional
    public ReconcileResponse reconcile(UUID userId, UUID walletId, boolean autoFix) {
        Wallet wallet = walletRepository
                .findByIdForUser(walletId, userId)
                .orElseThrow(() -> new BusinessException(
                        "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy ví."));

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
            log.warn(
                    "Lệch số dư ví phát hiện khi reconcile: walletId={}, storedBalance={}, "
                            + "computedBalance={}, difference={}, timestamp={}",
                    walletId, storedBalance, computedBalance, difference, Instant.now());

            if (autoFix) {
                walletRepository.adjustBalance(walletId, -difference);
                wasFixed = true;
            }
        }

        return new ReconcileResponse(walletId, storedBalance, computedBalance, difference, difference == 0, wasFixed);
    }

    /**
     * Lock ví theo id rồi kiểm tra quyền D-27 (Phase 2 chỉ có nhánh cá nhân, nhóm mãi Phase 5).
     * Không tồn tại hoặc không thuộc quyền -> NOT_FOUND 404, không tiết lộ ví có tồn tại hay
     * không (T-02-13).
     */
    private Wallet lockAndCheckOwnership(UUID walletId, UUID userId) {
        Wallet wallet = walletRepository
                .findByIdForUpdate(walletId)
                .orElseThrow(() -> new BusinessException(
                        "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy ví."));
        if (!wallet.getUserId().equals(userId)) {
            throw new BusinessException("NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy ví.");
        }
        return wallet;
    }
}

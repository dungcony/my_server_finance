package com.datn.financeapp.wallet.service;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.wallet.dto.TransferRequest;
import com.datn.financeapp.wallet.dto.TransferResponse;
import com.datn.financeapp.wallet.entity.Wallet;
import com.datn.financeapp.wallet.repository.WalletRepository;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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

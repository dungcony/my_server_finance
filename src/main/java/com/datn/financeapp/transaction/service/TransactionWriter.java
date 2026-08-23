package com.datn.financeapp.transaction.service;

import com.datn.financeapp.transaction.entity.Transaction;
import com.datn.financeapp.transaction.repository.TransactionRepository;
import com.datn.financeapp.wallet.repository.WalletRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bean hạ tầng riêng (D-31) — chỉ lo "ghi bản ghi giao dịch + cập nhật số dư ví cho đúng", KHÔNG
 * biết gì về nghiệp vụ gọi nó. Ba nơi cùng gọi: {@code TransactionService} (CRUD người dùng),
 * {@code WalletTransferService} (chuyển tiền + kiểm kê), và Phase 4 (debt/goal/recurring).
 *
 * <p>{@code @Component} (không phải {@code @Service}) vì đây là bean hạ tầng, không phải service
 * nghiệp vụ. Chỉ một public method mang annotation quản lý giao dịch DB riêng — không đặt cấp
 * class, và không gọi method khác cùng bean qua {@code this.xxx()}, tránh mất proxy AOP do
 * self-invocation (mẫu tham chiếu: {@code common.idempotency.IdempotencyTransactionHelper}).
 */
@Component
@RequiredArgsConstructor
public class TransactionWriter {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;

    /**
     * Ghi một giao dịch mới + cập nhật số dư (các) ví liên quan theo đúng chiều tiền của
     * {@code type} (api/04-GIAO-DICH.md mục "Cách số dư thay đổi"):
     * <ul>
     *   <li>{@code expense} → trừ ví nguồn</li>
     *   <li>{@code income} → cộng ví nguồn</li>
     *   <li>{@code transfer} → trừ ví nguồn, cộng ví đích</li>
     * </ul>
     * Đọc lại số dư mới bằng {@code walletRepository.findCurrentBalanceNative(...)} SAU khi
     * {@code adjustBalance} — vì {@code adjustBalance} là {@code @Modifying} UPDATE trực tiếp,
     * không tự đồng bộ persistence context, nên phải query lại thay vì cộng tay trong Java.
     * Dùng bản native (không qua entity manager) thay vì {@code findByIdForUpdate}: nếu caller
     * (vd. {@code WalletTransferService.transfer()}) đã load ví này qua JPQL trong CÙNG
     * transaction trước khi gọi {@code write()}, {@code findByIdForUpdate} sẽ trả lại đúng
     * instance đã cache trong Hibernate identity map — mang số dư CŨ trước UPDATE.
     */
    @Transactional
    public WriteResult write(TransactionWriteCommand cmd) {
        UUID transactionId = cmd.id() != null ? cmd.id() : UUID.randomUUID();
        Instant now = Instant.now();

        Transaction entity = Transaction.builder()
                .id(transactionId)
                .userId(cmd.userId())
                .walletId(cmd.walletId())
                .destinationWalletId(cmd.destinationWalletId())
                .categoryId(cmd.categoryId())
                .type(cmd.type())
                .amount(cmd.amount())
                .date(cmd.date())
                .note(cmd.note())
                .displayName(cmd.displayName())
                .source(cmd.source())
                .recurringId(cmd.recurringId())
                .draftId(cmd.draftId())
                .receiptUrl(cmd.receiptUrl())
                .isDeleted(false)
                .createdAt(now)
                // updated_at do trigger trg_transactions_validate tự set khi INSERT — set tường
                // minh vẫn an toàn (trigger ghi đè) nhưng không dựa vào giá trị này để đọc lại.
                .updatedAt(now)
                .countsInReport(cmd.countsInReport())
                .build();
        transactionRepository.save(entity);

        long walletNewBalance;
        Long destinationWalletNewBalance = null;

        switch (cmd.type()) {
            case "expense" -> {
                walletRepository.adjustBalance(cmd.walletId(), -cmd.amount());
                walletNewBalance = walletRepository
                        .findCurrentBalanceNative(cmd.walletId())
                        .orElseThrow();
            }
            case "income" -> {
                walletRepository.adjustBalance(cmd.walletId(), cmd.amount());
                walletNewBalance = walletRepository
                        .findCurrentBalanceNative(cmd.walletId())
                        .orElseThrow();
            }
            case "transfer" -> {
                walletRepository.adjustBalance(cmd.walletId(), -cmd.amount());
                walletRepository.adjustBalance(cmd.destinationWalletId(), cmd.amount());
                walletNewBalance = walletRepository
                        .findCurrentBalanceNative(cmd.walletId())
                        .orElseThrow();
                destinationWalletNewBalance = walletRepository
                        .findCurrentBalanceNative(cmd.destinationWalletId())
                        .orElseThrow();
            }
            default -> throw new IllegalArgumentException("Loại giao dịch không hợp lệ: " + cmd.type());
        }

        return new WriteResult(transactionId, walletNewBalance, destinationWalletNewBalance);
    }

    /**
     * Kết quả ghi giao dịch. {@code destinationWalletNewBalance} chỉ khác NULL khi
     * {@code type = transfer}.
     */
    public record WriteResult(UUID transactionId, long walletNewBalance, Long destinationWalletNewBalance) {
    }
}

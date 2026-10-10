package com.datn.financeapp.module.transaction.helper;

import com.datn.financeapp.module.budget.dto.response.BudgetAffectedRes;
import com.datn.financeapp.module.budget.service.BudgetSpendService;
import com.datn.financeapp.module.transaction.entity.Transaction;
import com.datn.financeapp.module.transaction.repository.TransactionRepository;
import com.datn.financeapp.module.wallet.service.WalletService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Component hạ tầng ghi nhận giao dịch và cập nhật số dư các ví liên quan.
 * Cung cấp các phương thức ghi như {@link #write(TransactionWriteCommand)} và {@link #writeAndFlush(TransactionWriteCommand)}.
 */
@Component
@RequiredArgsConstructor
public class TransactionWriter {

    private final TransactionRepository transactionRepository;
    private final WalletService walletService;
    private final BudgetSpendService budgetSpendService;

    /**
     * Ghi mới một giao dịch và cập nhật lại số dư ví tương ứng.
     *
     * @param cmd lệnh thông tin ghi giao dịch
     * @return {@link WriteResult} chứa thông tin ID giao dịch và số dư ví mới
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
                .updatedAt(now)
                .countsInReport(cmd.countsInReport())
                .build();
        transactionRepository.save(entity);

        long walletNewBalance;
        Long destinationWalletNewBalance = null;

        switch (cmd.type()) {
            case "expense" -> {
                walletService.adjustBalance(cmd.walletId(), -cmd.amount());
                walletNewBalance = walletService.requireRawBalance(cmd.walletId());
            }
            case "income" -> {
                walletService.adjustBalance(cmd.walletId(), cmd.amount());
                walletNewBalance = walletService.requireRawBalance(cmd.walletId());
            }
            case "transfer" -> {
                walletService.adjustBalance(cmd.walletId(), -cmd.amount());
                walletService.adjustBalance(cmd.destinationWalletId(), cmd.amount());
                walletNewBalance = walletService.requireRawBalance(cmd.walletId());
                destinationWalletNewBalance = walletService.requireRawBalance(cmd.destinationWalletId());
            }
            default -> throw new IllegalArgumentException("Loại giao dịch không hợp lệ: " + cmd.type());
        }

        // Chỉ khoản chi tính vào báo cáo mới ăn vào ngân sách; giao dịch bù kiểm kê (counts_in_report = false) thì không
        List<BudgetAffectedRes> affectedBudgets = "expense".equals(cmd.type()) && cmd.countsInReport()
                ? budgetSpendService.addExpense(cmd.userId(), cmd.categoryId(), cmd.date(), cmd.amount())
                : List.of();

        return new WriteResult(transactionId, walletNewBalance, destinationWalletNewBalance, affectedBudgets);
    }

    /**
     * Ghi mới một giao dịch và ép đẩy (flush) ngay xuống cơ sở dữ liệu.
     *
     * @param cmd lệnh thông tin ghi giao dịch
     * @return {@link WriteResult} chứa thông tin ID giao dịch và số dư ví mới
     */
    public WriteResult writeAndFlush(TransactionWriteCommand cmd) {
        WriteResult result = write(cmd);
        transactionRepository.flush();
        return result;
    }

    /**
     * Record mô tả kết quả sau khi ghi nhận giao dịch.
     *
     * @param transactionId               ID giao dịch vừa ghi
     * @param walletNewBalance            số dư mới của ví nguồn
     * @param destinationWalletNewBalance số dư mới của ví đích (nếu là chuyển tiền)
     * @param affectedBudgets             các ngân sách bị khoản chi ảnh hưởng, rỗng nếu không phải khoản chi
     */
    public record WriteResult(
            UUID transactionId,
            long walletNewBalance,
            Long destinationWalletNewBalance,
            List<BudgetAffectedRes> affectedBudgets) {
    }
}

package com.datn.financeapp.module.transaction.service;

import com.datn.financeapp.module.transaction.entity.Transaction;
import com.datn.financeapp.module.transaction.event.TransactionRecordedEvent;
import com.datn.financeapp.module.transaction.repository.TransactionRepository;
import com.datn.financeapp.module.wallet.service.WalletService;

import java.time.Instant;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;

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

        eventPublisher.publishEvent(new TransactionRecordedEvent(
                transactionId, cmd.userId(), cmd.walletId(), cmd.categoryId(),
                cmd.type(), cmd.amount(), cmd.date()));

        return new WriteResult(transactionId, walletNewBalance, destinationWalletNewBalance);
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
     */
    public record WriteResult(UUID transactionId, long walletNewBalance, Long destinationWalletNewBalance) {
    }
}

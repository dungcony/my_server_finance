package com.datn.financeapp.module.transaction.helper;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.budget.dto.response.BudgetAffectedRes;
import com.datn.financeapp.module.budget.service.BudgetSpendService;
import com.datn.financeapp.module.transaction.entity.Transaction;
import com.datn.financeapp.module.transaction.enums.TransactionType;
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
 * Cung cấp các phương thức ghi như {@link #write(TransactionWriteCommand)} và {@link #writeAndFlush(TransactionWriteCommand)},
 * ghi đè một giao dịch đã có bằng {@link #overwrite(TransactionWriteCommand)} và xoá mềm bằng {@link #remove(UUID, UUID)}.
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
        TransactionType type = cmd.type();

        Transaction entity = Transaction.builder()
                .id(transactionId)
                .userId(cmd.userId())
                .walletId(cmd.walletId())
                .categoryId(cmd.categoryId())
                .type(type)
                .amount(cmd.amount())
                .date(cmd.date())
                .note(cmd.note())
                .name(cmd.name())
                .source(cmd.source())
                .recurringId(cmd.recurringId())
                .receiptUrl(cmd.receiptUrl())
                .isDeleted(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
        transactionRepository.save(entity);

        return applyEffect(entity);
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
     * Ghi đè một giao dịch đã có: hoàn tác ảnh hưởng cũ lên ví và ngân sách, cập nhật các trường
     * theo lệnh rồi áp ảnh hưởng mới. {@code source} và {@code recurringId} giữ nguyên.
     *
     * @param cmd lệnh ghi, {@code id} và {@code userId} bắt buộc để tìm đúng giao dịch của người dùng
     * @return {@link WriteResult} chứa số dư mới của ví sau khi áp ảnh hưởng mới
     * @throws BusinessException nếu không có giao dịch chưa xoá với {@code id} và {@code userId} này
     */
    @Transactional
    public WriteResult overwrite(TransactionWriteCommand cmd) {
        Transaction entity = transactionRepository
                .findByIdAndUserIdAndIsDeletedFalse(cmd.id(), cmd.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch."));

        revertEffect(entity);

        entity.setWalletId(cmd.walletId());
        entity.setCategoryId(cmd.categoryId());
        entity.setType(cmd.type());
        entity.setAmount(cmd.amount());
        entity.setDate(cmd.date());
        entity.setNote(cmd.note());
        entity.setName(cmd.name());
        entity.setReceiptUrl(cmd.receiptUrl());
        entity.setUpdatedAt(Instant.now());
        transactionRepository.save(entity);

        return applyEffect(entity);
    }

    /**
     * Xoá mềm một giao dịch và hoàn tác ảnh hưởng của nó lên ví và ngân sách. Không có giao dịch
     * (chưa tồn tại hoặc đã xoá) thì bỏ qua, gọi lại nhiều lần không ghi hoàn tác thêm.
     *
     * @param userId        ID người dùng sở hữu giao dịch
     * @param transactionId ID giao dịch cần xoá
     */
    @Transactional
    public void remove(UUID userId, UUID transactionId) {
        transactionRepository.findByIdAndUserIdAndIsDeletedFalse(transactionId, userId).ifPresent(entity -> {
            revertEffect(entity);
            entity.setIsDeleted(true);
            entity.setUpdatedAt(Instant.now());
            transactionRepository.save(entity);
        });
    }

    // áp ảnh hưởng của giao dịch lên số dư ví và ngân sách
    private WriteResult applyEffect(Transaction entity) {
        walletService.adjustBalance(entity.getWalletId(), walletDelta(entity));
        long walletNewBalance = walletService.requireRawBalance(entity.getWalletId());

        List<BudgetAffectedRes> affectedBudgets = isSpending(entity)
                ? budgetSpendService.addExpense(
                        entity.getUserId(), entity.getCategoryId(), entity.getDate(), entity.getAmount())
                : List.of();

        return new WriteResult(entity.getId(), walletNewBalance, affectedBudgets);
    }

    // hoàn tác đúng phần ảnh hưởng mà applyEffect đã áp
    private void revertEffect(Transaction entity) {
        walletService.adjustBalance(entity.getWalletId(), -walletDelta(entity));

        if (isSpending(entity)) {
            budgetSpendService.removeExpense(
                    entity.getUserId(), entity.getCategoryId(), entity.getDate(), entity.getAmount());
        }
    }

    // INCOME cộng vào ví, EXPENSE và LOAN (cho vay) trừ khỏi ví
    private long walletDelta(Transaction entity) {
        return entity.getType() == TransactionType.INCOME ? entity.getAmount() : -entity.getAmount();
    }

    // chỉ khoản chi (EXPENSE, LOAN) mới ăn vào ngân sách
    private boolean isSpending(Transaction entity) {
        return entity.getType() == TransactionType.EXPENSE || entity.getType() == TransactionType.LOAN;
    }

    /**
     * Record mô tả kết quả sau khi ghi nhận giao dịch.
     *
     * @param transactionId               ID giao dịch vừa ghi
     * @param walletNewBalance            số dư mới của ví nguồn
     * @param affectedBudgets             các ngân sách bị khoản chi ảnh hưởng, rỗng nếu không phải khoản chi
     */
    public record WriteResult(
            UUID transactionId,
            long walletNewBalance,
            List<BudgetAffectedRes> affectedBudgets) {
    }
}

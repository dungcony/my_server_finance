package com.datn.financeapp.module.transaction.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.budget.service.BudgetSpendService;
import com.datn.financeapp.module.transaction.entity.Transaction;
import com.datn.financeapp.module.transaction.enums.TransactionSource;
import com.datn.financeapp.module.transaction.enums.TransactionType;
import com.datn.financeapp.module.transaction.repository.TransactionRepository;
import com.datn.financeapp.module.wallet.service.WalletService;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionWriterTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 1);

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private WalletService walletService;

    @Mock
    private BudgetSpendService budgetSpendService;

    @InjectMocks
    private TransactionWriter writer;

    private final UUID userId = UUID.randomUUID();
    private final UUID walletId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();

    private TransactionWriteCommand command(UUID id, UUID wallet, TransactionType type, long amount) {
        return new TransactionWriteCommand(
                id, userId, wallet, categoryId, type, amount, DATE, "ghi chú", "tên", TransactionSource.MANUAL, null, null);
    }

    private Transaction existing(TransactionType type, long amount) {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .walletId(walletId)
                .categoryId(categoryId)
                .type(type)
                .amount(amount)
                .date(DATE)
                .source(TransactionSource.MANUAL)
                .isDeleted(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("write EXPENSE: trừ ví và cộng vào ngân sách")
    void write_expense_debitsWalletAndAddsBudgetSpend() {
        when(walletService.requireRawBalance(walletId)).thenReturn(900L);

        TransactionWriter.WriteResult result = writer.write(command(null, walletId, TransactionType.EXPENSE, 100L));

        verify(walletService).adjustBalance(walletId, -100L);
        verify(budgetSpendService).addExpense(userId, categoryId, DATE, 100L);
        assertThat(result.walletNewBalance()).isEqualTo(900L);
        assertThat(result.transactionId()).isNotNull();
    }

    @Test
    @DisplayName("write INCOME: cộng ví và không đụng ngân sách")
    void write_income_creditsWalletWithoutBudget() {
        when(walletService.requireRawBalance(walletId)).thenReturn(1_100L);

        writer.write(command(null, walletId, TransactionType.INCOME, 100L));

        verify(walletService).adjustBalance(walletId, 100L);
        verifyNoInteractions(budgetSpendService);
    }

    @Test
    @DisplayName("write LOAN: trừ ví và tính vào ngân sách giống EXPENSE")
    void write_loan_behavesLikeExpense() {
        writer.write(command(null, walletId, TransactionType.LOAN, 300L));

        verify(walletService).adjustBalance(walletId, -300L);
        verify(budgetSpendService).addExpense(userId, categoryId, DATE, 300L);
    }

    @Test
    @DisplayName("write dùng id của lệnh nếu có để id giao dịch cá nhân trùng id giao dịch nhóm")
    void write_withExplicitId_keepsThatId() {
        UUID id = UUID.randomUUID();

        TransactionWriter.WriteResult result = writer.write(command(id, walletId, TransactionType.EXPENSE, 50L));

        assertThat(result.transactionId()).isEqualTo(id);
    }

    @Test
    @DisplayName("overwrite: hoàn tác ví và ngân sách cũ rồi áp ví và ngân sách mới")
    void overwrite_revertsOldEffectThenAppliesNewOne() {
        Transaction old = existing(TransactionType.EXPENSE, 100L);
        UUID newWalletId = UUID.randomUUID();
        when(transactionRepository.findByIdAndUserIdAndIsDeletedFalse(old.getId(), userId))
                .thenReturn(Optional.of(old));
        when(walletService.requireRawBalance(newWalletId)).thenReturn(5_050L);

        TransactionWriter.WriteResult result =
                writer.overwrite(command(old.getId(), newWalletId, TransactionType.INCOME, 50L));

        // hoàn tác khoản chi cũ 100 ở ví cũ
        verify(walletService).adjustBalance(walletId, 100L);
        verify(budgetSpendService).removeExpense(userId, categoryId, DATE, 100L);
        // áp khoản thu mới 50 ở ví mới, khoản thu không đụng ngân sách
        verify(walletService).adjustBalance(newWalletId, 50L);
        verify(budgetSpendService, never()).addExpense(any(), any(), any(), anyLong());
        assertThat(old.getType()).isEqualTo(TransactionType.INCOME);
        assertThat(old.getAmount()).isEqualTo(50L);
        assertThat(old.getWalletId()).isEqualTo(newWalletId);
        assertThat(old.getName()).isEqualTo("tên");
        assertThat(result.walletNewBalance()).isEqualTo(5_050L);
    }

    @Test
    @DisplayName("overwrite khi không có giao dịch: báo NOT_FOUND và không đụng ví")
    void overwrite_missingTransaction_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(transactionRepository.findByIdAndUserIdAndIsDeletedFalse(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> writer.overwrite(command(id, walletId, TransactionType.EXPENSE, 10L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.NOT_FOUND.getCode());

        verifyNoInteractions(walletService, budgetSpendService);
    }

    @Test
    @DisplayName("remove EXPENSE: xoá mềm, hoàn ví và hoàn ngân sách")
    void remove_expense_softDeletesAndRevertsEffects() {
        Transaction old = existing(TransactionType.EXPENSE, 100L);
        when(transactionRepository.findByIdAndUserIdAndIsDeletedFalse(old.getId(), userId))
                .thenReturn(Optional.of(old));

        writer.remove(userId, old.getId());

        assertThat(old.getIsDeleted()).isTrue();
        verify(walletService).adjustBalance(walletId, 100L);
        verify(budgetSpendService).removeExpense(userId, categoryId, DATE, 100L);
        verify(transactionRepository).save(old);
    }

    @Test
    @DisplayName("remove INCOME: trừ lại ví và không đụng ngân sách")
    void remove_income_debitsWalletWithoutBudget() {
        Transaction old = existing(TransactionType.INCOME, 70L);
        when(transactionRepository.findByIdAndUserIdAndIsDeletedFalse(old.getId(), userId))
                .thenReturn(Optional.of(old));

        writer.remove(userId, old.getId());

        verify(walletService).adjustBalance(walletId, -70L);
        verifyNoInteractions(budgetSpendService);
    }

    @Test
    @DisplayName("remove khi không có giao dịch (chưa tồn tại hoặc đã xoá): bỏ qua, không hoàn tác thêm")
    void remove_missingTransaction_isNoOp() {
        UUID id = UUID.randomUUID();
        when(transactionRepository.findByIdAndUserIdAndIsDeletedFalse(id, userId)).thenReturn(Optional.empty());

        writer.remove(userId, id);

        verifyNoInteractions(walletService, budgetSpendService);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("write trả danh sách ngân sách bị ảnh hưởng của khoản chi")
    void write_expense_returnsAffectedBudgets() {
        when(budgetSpendService.addExpense(userId, categoryId, DATE, 10L)).thenReturn(List.of());

        TransactionWriter.WriteResult result = writer.write(command(null, walletId, TransactionType.EXPENSE, 10L));

        assertThat(result.affectedBudgets()).isEmpty();
    }
}

package com.datn.financeapp.module.budget.events.listener;

import com.datn.financeapp.module.budget.service.BudgetSpendService;
import com.datn.financeapp.module.transaction.entity.Transaction;
import com.datn.financeapp.module.transaction.enums.TxnType;
import com.datn.financeapp.module.transaction.events.TxnCreateEvent;
import com.datn.financeapp.module.transaction.events.TxnDeleteEvent;
import com.datn.financeapp.module.transaction.events.TxnUpdateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Lắng nghe các sự kiện giao dịch tài chính cá nhân để cập nhật số đã tiêu (spent) của các ngân sách liên quan.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BudgetTxnEventListener {

    private final BudgetSpendService budgetSpendService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onTxnCreated(TxnCreateEvent event) {
        Transaction txn = event.txn();
        if (txn != null && txn.getType() == TxnType.EXPENSE && txn.getCategoryCode() != null) {
            budgetSpendService.addExpense(txn.getUserId(), txn.getCategoryCode(), txn.getOccurredAt(), txn.getAmount());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onTxnUpdated(TxnUpdateEvent event) {
        Transaction oldTxn = event.oldTxn();
        Transaction newTxn = event.newTxn();

        if (oldTxn != null && oldTxn.getType() == TxnType.EXPENSE && oldTxn.getCategoryCode() != null) {
            budgetSpendService.removeExpense(oldTxn.getUserId(), oldTxn.getCategoryCode(), oldTxn.getOccurredAt(), oldTxn.getAmount());
        }
        if (newTxn != null && newTxn.getType() == TxnType.EXPENSE && newTxn.getCategoryCode() != null) {
            budgetSpendService.addExpense(newTxn.getUserId(), newTxn.getCategoryCode(), newTxn.getOccurredAt(), newTxn.getAmount());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onTxnDeleted(TxnDeleteEvent event) {
        Transaction txn = event.txn();
        if (txn != null && txn.getType() == TxnType.EXPENSE && txn.getCategoryCode() != null) {
            budgetSpendService.removeExpense(txn.getUserId(), txn.getCategoryCode(), txn.getOccurredAt(), txn.getAmount());
        }
    }
}

package com.datn.financeapp.module.transaction.events;

import com.datn.financeapp.module.transaction.entity.Transaction;

public record TxnUpdateEvent(Transaction oldTxn, Transaction newTxn) {
}

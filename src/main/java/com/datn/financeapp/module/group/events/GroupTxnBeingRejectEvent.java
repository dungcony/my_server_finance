package com.datn.financeapp.module.group.events;

import com.datn.financeapp.module.group.entity.GroupTxn;
import com.datn.financeapp.module.group.enums.GroupTxnType;
import com.datn.financeapp.module.group.enums.MoneySource;

import java.time.Instant;
import java.util.UUID;

/**
 *
 * @param groupTxnId
 * @param groupId
 * @param type
 * @param moneySource
 * @param amount
 * @param transactorId
 * @param categoryCode
 * @param occurredAt
 * @param reson        Lý do từ chối
 */
public record GroupTxnBeingRejectEvent(
        UUID groupTxnId,
        UUID groupId,
        GroupTxnType type,
        MoneySource moneySource,
        long amount,
        UUID transactorId,
        String categoryCode,
        Instant occurredAt,

        String reson // lý do từ  chối
) {
    public static GroupTxnBeingRejectEvent of(GroupTxn txn, String reason) {

        return new GroupTxnBeingRejectEvent(
                txn.getId(),
                txn.getGroupId(),
                txn.getType(),
                txn.getMoneySource(),
                txn.getAmount(),
                txn.getTransactorId(),
                txn.getCategoryCode(),
                txn.getOccurredAt(),
                reason);
    }
}

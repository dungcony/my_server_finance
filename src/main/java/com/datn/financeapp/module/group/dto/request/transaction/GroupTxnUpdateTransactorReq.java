package com.datn.financeapp.module.group.dto.request.transaction;

import java.util.UUID;

public record GroupTxnUpdateTransactorReq(
        UUID txnId,
        UUID oldTransactorId,
        UUID newTransactorId
) {
}

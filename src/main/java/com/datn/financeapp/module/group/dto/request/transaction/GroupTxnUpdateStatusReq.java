package com.datn.financeapp.module.group.dto.request.transaction;

import com.datn.financeapp.module.group.enums.GroupTxnStatus;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record GroupTxnUpdateStatusReq(
        @NotNull(message = "txn_id là bắt buộc") UUID txnId,
        @NotNull(message = "status là bắt buộc") GroupTxnStatus status,
        String reason
) {
}

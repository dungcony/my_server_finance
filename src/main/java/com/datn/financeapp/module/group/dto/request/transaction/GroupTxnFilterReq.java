package com.datn.financeapp.module.group.dto.request.transaction;

import com.datn.financeapp.module.group.enums.GroupTxnStatus;
import com.datn.financeapp.module.group.enums.GroupTxnType;
import com.datn.financeapp.module.group.enums.MoneySource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record GroupTxnFilterReq(
        MoneySource moneySource,
        GroupTxnType type,
        UUID transactorId,
        UUID createId,
        Instant fromOccurredAt,
        Instant toOccurredAt,
        LocalDate startDate,
        LocalDate endDate,
        Integer page,
        Integer size
) {

    public Instant resolveFrom() {
        if (fromOccurredAt != null) return fromOccurredAt;
        if (startDate != null) return startDate.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        return null;
    }

    public Instant resolveTo() {
        if (toOccurredAt != null) return toOccurredAt;
        if (endDate != null) return endDate.plusDays(1).atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        return null;
    }
}

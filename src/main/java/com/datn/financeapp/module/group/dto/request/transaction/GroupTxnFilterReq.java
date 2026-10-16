package com.datn.financeapp.module.group.dto.request.transaction;

import com.datn.financeapp.module.group.enums.GroupTxnType;
import com.datn.financeapp.module.group.enums.MoneySource;
import org.springframework.web.bind.annotation.BindParam;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

public record GroupTxnFilterReq(
        @BindParam("money_source") MoneySource moneySource,
        GroupTxnType type,
        @BindParam("transactor_id") UUID transactorId,
        @BindParam("create_id") UUID createId,
        @BindParam("from_occurred_at") Instant fromOccurredAt,
        @BindParam("to_occurred_at") Instant toOccurredAt,
        @BindParam("start_date") LocalDate startDate,
        @BindParam("end_date") LocalDate endDate,
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
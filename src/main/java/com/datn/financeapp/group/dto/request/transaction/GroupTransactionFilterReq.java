package com.datn.financeapp.group.dto.request.transaction;

import com.datn.financeapp.group.enums.GTransactionStatus;
import com.datn.financeapp.group.enums.GTransactionType;
import com.datn.financeapp.group.enums.MoneySource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

public record GroupTransactionFilterReq(
        MoneySource moneySource,
        GTransactionType type,
        GTransactionStatus status,
        UUID transactorId,
        Instant fromOccurredAt,
        Instant toOccurredAt,
        LocalDate startDate,
        LocalDate endDate,
        Integer page,
        Integer size,
        GTransactionStatus excludeStatus,
        UUID createdBy) {

    public GroupTransactionFilterReq(
            MoneySource moneySource, GTransactionType type, GTransactionStatus status,
            UUID transactorId, Instant fromOccurredAt, Instant toOccurredAt,
            LocalDate startDate, LocalDate endDate, Integer page, Integer size) {
        this(moneySource, type, status, transactorId, fromOccurredAt, toOccurredAt,
                startDate, endDate, page, size, null, null);
    }

    public GroupTransactionFilterReq withExcludeStatus(GTransactionStatus excludeStatus) {
        return new GroupTransactionFilterReq(
                moneySource, type, status, transactorId, fromOccurredAt, toOccurredAt,
                startDate, endDate, page, size, excludeStatus, createdBy);
    }

    public GroupTransactionFilterReq withCreatedBy(UUID createdBy) {
        return new GroupTransactionFilterReq(
                moneySource, type, status, transactorId, fromOccurredAt, toOccurredAt,
                startDate, endDate, page, size, excludeStatus, createdBy);
    }

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

    public int getPageNumber() {
        return (page != null && page >= 0) ? page : 0;
    }

    public int getPageSize() {
        return (size != null && size > 0 && size <= 100) ? size : 20;
    }
}

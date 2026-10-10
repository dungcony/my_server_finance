package com.datn.financeapp.module.group.dto.request.transaction;

import com.datn.financeapp.module.group.enums.GTransactionStatus;
import com.datn.financeapp.module.group.enums.GTransactionType;
import com.datn.financeapp.module.group.enums.MoneySource;

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
        boolean excludeWaiting,
        UUID involvedUserId) {

    public GroupTransactionFilterReq(
            MoneySource moneySource, GTransactionType type, GTransactionStatus status,
            UUID transactorId, Instant fromOccurredAt, Instant toOccurredAt,
            LocalDate startDate, LocalDate endDate, Integer page, Integer size) {
        this(moneySource, type, status, transactorId, fromOccurredAt, toOccurredAt,
                startDate, endDate, page, size, false, null);
    }

    // loại giao dịch đang chờ xử lý (PENDING, AWAITING_TRANSACTOR), dùng cho member thường khi xem danh sách chung
    public GroupTransactionFilterReq withExcludeWaiting(boolean excludeWaiting) {
        return new GroupTransactionFilterReq(
                moneySource, type, status, transactorId, fromOccurredAt, toOccurredAt,
                startDate, endDate, page, size, excludeWaiting, involvedUserId);
    }

    // chỉ lấy giao dịch do người này tạo hoặc người này là người thực hiện
    public GroupTransactionFilterReq withInvolvedUserId(UUID involvedUserId) {
        return new GroupTransactionFilterReq(
                moneySource, type, status, transactorId, fromOccurredAt, toOccurredAt,
                startDate, endDate, page, size, excludeWaiting, involvedUserId);
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

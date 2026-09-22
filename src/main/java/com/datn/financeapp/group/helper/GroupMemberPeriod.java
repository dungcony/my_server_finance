package com.datn.financeapp.group.helper;

import java.time.Instant;
import java.util.UUID;

/**
 * Dữ liệu thời gian tham gia nhóm của từng thành viên để xác định sự có mặt tại thời điểm giao dịch.
 * Cho phép Service và Calculator tự động lọc in-memory, loại bỏ N+1 query.
 */
public record GroupMemberPeriod(
        UUID userId,
        Instant joinedAt,
        Instant leftAt) {
    public boolean isActiveAt(Instant occurredAt) {
        if (occurredAt == null || userId == null) {
            return false;
        }
        boolean joined = (joinedAt != null && !joinedAt.isAfter(occurredAt));
        boolean notLeft = (leftAt == null || leftAt.isAfter(occurredAt));
        return joined && notLeft;
    }
}

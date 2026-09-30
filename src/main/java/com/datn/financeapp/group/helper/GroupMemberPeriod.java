package com.datn.financeapp.group.helper;

import java.time.Instant;
import java.util.UUID;

/**
 * Dữ liệu thời gian tham gia nhóm của từng thành viên để xác định sự có mặt tại thời điểm giao dịch.
 * Cho phép Service và Calculator tự động lọc in-memory, loại bỏ N+1 query.
 * <p>
 * Các hàm trong class:
 * <ul>
 *   <li>{@link #isActiveAt(Instant)}: Kiểm tra thành viên có đang hoạt động. Input: thời điểm cần xét. Output: boolean.</li>
 * </ul>
 * </p>
 *
 * @param userId   ID của thành viên trong nhóm

 * @param joinedAt Thời điểm thành viên chính thức gia nhập nhóm
 * @param leftAt   Thời điểm thành viên rời khỏi nhóm (null nếu vẫn đang hoạt động trong nhóm)
 */
public record GroupMemberPeriod(
        UUID userId,
        Instant joinedAt,
        Instant leftAt) {

    /**
     * Kiểm tra xem thành viên có đang hoạt động trong nhóm tại thời điểm diễn ra giao dịch hay không.
     *
     * @param occurredAt Thời điểm diễn ra giao dịch cần kiểm tra
     * @return {@code true} nếu thành viên có mặt tại thời điểm đó, ngược lại {@code false}
     */
    public boolean isActiveAt(Instant occurredAt) {
        if (occurredAt == null || userId == null) {
            return false;
        }
        boolean joined = (joinedAt != null && !joinedAt.isAfter(occurredAt));
        boolean notLeft = (leftAt == null || leftAt.isAfter(occurredAt));
        return joined && notLeft;
    }
}

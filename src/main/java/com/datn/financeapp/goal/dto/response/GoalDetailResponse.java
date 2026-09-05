package com.datn.financeapp.goal.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Body của {@code GET /goals/{id}} — mục tiêu kèm lịch sử nạp (api/09 mục B1). */
public record GoalDetailResponse(GoalListItemResponse goal, List<ContributionHistoryItem> contributions) {

    /** {@code transactionId} bằng null nghĩa là lần nạp đó chỉ ghi nhận tiến độ, không chuyển tiền thật. */
    public record ContributionHistoryItem(UUID id, Long amount, LocalDate contributedDate, UUID transactionId) {}
}

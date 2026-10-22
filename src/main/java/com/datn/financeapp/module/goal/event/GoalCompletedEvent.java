package com.datn.financeapp.module.goal.event;

import java.util.UUID;

/**
 * Sự kiện phát ra khi mục tiêu tiết kiệm hoàn thành (status chuyển sang completed).
 *
 * @param userId       ID người dùng sở hữu mục tiêu
 * @param goalId       ID mục tiêu tiết kiệm
 * @param goalName     Tên mục tiêu
 * @param targetAmount Số tiền mục tiêu
 */
public record GoalCompletedEvent(
        UUID userId,
        UUID goalId,
        String goalName,
        long targetAmount
) {
}

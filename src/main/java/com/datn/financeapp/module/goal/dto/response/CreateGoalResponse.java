package com.datn.financeapp.module.goal.dto.response;

/**
 * Thông tin phản hồi tạo mới mục tiêu tiết kiệm.
 *
 * @param goal Thông tin tóm tắt mục tiêu tiết kiệm vừa tạo
 */
public record CreateGoalResponse(GoalListItemResponse goal) {
}

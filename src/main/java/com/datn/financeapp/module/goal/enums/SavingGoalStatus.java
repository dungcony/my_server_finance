package com.datn.financeapp.module.goal.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Trạng thái của mục tiêu tích lũy tài chính:
 * <ul>
 *   <li>{@link #IN_PROGRESS}: Đang trong quá trình tích lũy</li>
 *   <li>{@link #COMPLETED}: Đã hoàn thành (số tiền tích lũy >= mục tiêu)</li>
 *   <li>{@link #CANCELLED}: Đã bị hủy bỏ bởi người dùng</li>
 * </ul>
 */
public enum SavingGoalStatus {
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;
}

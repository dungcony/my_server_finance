package com.datn.financeapp.module.debt.dto.response;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Projection tối giản dùng cho {@code DebtReminderJob}.
 * Chỉ lấy đúng các cột cần thiết — không hydrate full entity vào Hibernate context.
 */
public interface DebtReminderView {
    UUID getId();
    UUID getUserId();
    String getName();
    LocalDate getDueDate();
}

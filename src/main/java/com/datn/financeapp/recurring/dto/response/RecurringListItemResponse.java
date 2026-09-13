package com.datn.financeapp.recurring.dto.response;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Một phần tử của {@code GET /recurring} (api/09 mục A1), cũng dùng làm phản hồi của
 * {@code POST}/{@code PATCH}/{@code pause}.
 *
 * <p>Ba trường được TÍNH SẴN ở server, không để app tự ghép:
 *
 * <ul>
 *   <li>{@code scheduleLabel} — câu tiếng Việt mô tả lịch ("Hằng tháng vào ngày 1"), đặc tả yêu
 *       cầu app hiển thị thẳng chứ không tự nối chuỗi từ {@code frequency} và {@code interval}.
 *   <li>{@code daysUntilNext} — số ngày còn lại tới kỳ kế tiếp.
 *   <li>{@code runCount} — số giao dịch đã sinh từ khoản này.
 * </ul>
 */
public record RecurringListItemResponse(
        UUID id,
        String displayName,
        String type,
        Long amount,
        WalletRef wallet,
        CategoryRef category,
        String frequency,
        Integer interval,
        String scheduleLabel,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate nextRunDate,
        LocalDate lastRunDate,
        Long daysUntilNext,
        Boolean isEnabled,
        Long runCount,
        String note) {

    public record WalletRef(UUID id, String name) {}

    public record CategoryRef(UUID id, String name, IconRef icon, String color) {}

    public record IconRef(String code, String pathData) {}
}

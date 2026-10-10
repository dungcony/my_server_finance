package com.datn.financeapp.module.transaction.helper;

import java.time.LocalDate;

/**
 * Utility hỗ trợ tính toán ngày chạy tiếp theo cho giao dịch định kỳ.
 * Cung cấp phương thức chính {@link #nextRunDate(LocalDate, LocalDate, String, int)} xử lý các ca đặc biệt về ngày cuối tháng.
 */
public final class RecurringDateCalculator {

    private RecurringDateCalculator() {
    }

    /**
     * Tính toán ngày chạy tiếp theo dựa trên ngày bắt đầu gốc và kỳ hiện tại.
     *
     * @param originalStartDate ngày bắt đầu gốc của khoản định kỳ
     * @param currentCursor     mốc kỳ vừa xử lý xong
     * @param frequency         tần suất lặp (day, week, month, year)
     * @param interval          khoảng thời gian giữa các lần lặp (>= 1)
     * @return {@link LocalDate} ngày chạy tiếp theo đã tính toán
     */
    public static LocalDate nextRunDate(
            LocalDate originalStartDate, LocalDate currentCursor, String frequency, int interval) {
        int originalDay = originalStartDate.getDayOfMonth();
        return switch (frequency) {
            // day/week không bao giờ gặp ca ngày không tồn tại nên cộng thẳng.
            case "day" -> currentCursor.plusDays(interval);
            case "week" -> currentCursor.plusWeeks(interval);
            case "month" -> applyOriginalDay(currentCursor.plusMonths(interval), originalDay);
            case "year" -> applyOriginalDay(currentCursor.plusYears(interval), originalDay);
            default -> throw new IllegalArgumentException("Tần suất không hợp lệ: " + frequency);
        };
    }

    /**
     * Ép ngày trong tháng về đúng ngày gốc, hoặc ngày cuối tháng nếu tháng đích ngắn hơn (api/09:
     * "nếu tháng không có ngày đó thì lấy ngày cuối tháng").
     */
    private static LocalDate applyOriginalDay(LocalDate target, int originalDay) {
        int lastDayOfTargetMonth = target.lengthOfMonth();
        int dayToUse = Math.min(originalDay, lastDayOfTargetMonth);
        return target.withDayOfMonth(dayToUse);
    }
}

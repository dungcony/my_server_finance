package com.datn.financeapp.module.transaction.helper;

import com.datn.financeapp.module.transaction.enums.FreequenType;

import java.time.LocalDate;

/**
 * Utility hỗ trợ tính toán ngày chạy tiếp theo cho giao dịch định kỳ.
 * Cung cấp phương thức chính {@link #nextRunDate(LocalDate, LocalDate, FreequenType, int)} xử lý các ca đặc biệt về ngày cuối tháng.
 */
public final class RecurringDateCalculator {

    private RecurringDateCalculator() {
    }

    /**
     * Tính toán ngày chạy tiếp theo dựa trên ngày bắt đầu gốc và kỳ hiện tại.
     *
     * @param originalStartDate ngày bắt đầu gốc của khoản định kỳ
     * @param currentCursor     mốc kỳ vừa xử lý xong
     * @param frequency         tần suất lặp (WEEK, MONTH, QUARTER, YEAR)
     * @param interval          khoảng thời gian giữa các lần lặp (>= 1)
     * @return {@link LocalDate} ngày chạy tiếp theo đã tính toán
     */
    public static LocalDate nextRunDate(
            LocalDate originalStartDate, LocalDate currentCursor, FreequenType frequency, int interval) {
        int originalDay = originalStartDate.getDayOfMonth();
        return switch (frequency) {
            // tuần không bao giờ gặp ca ngày không tồn tại nên cộng thẳng.
            case WEEK -> currentCursor.plusWeeks(interval);
            case MONTH -> applyOriginalDay(currentCursor.plusMonths(interval), originalDay);
            // một quý là 3 tháng
            case QUARTER -> applyOriginalDay(currentCursor.plusMonths(3L * interval), originalDay);
            case YEAR -> applyOriginalDay(currentCursor.plusYears(interval), originalDay);
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

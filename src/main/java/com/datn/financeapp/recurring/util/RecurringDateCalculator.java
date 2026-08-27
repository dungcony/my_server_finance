package com.datn.financeapp.recurring.util;

import java.time.LocalDate;

/**
 * Tính ngày chạy kế tiếp của một khoản định kỳ (api/09-DINH-KY-MUC-TIEU.md mục "Xử lý ngày không
 * tồn tại", D-50).
 *
 * <p>Utility thuần Java — cố ý KHÔNG phải Spring bean, không import gì từ
 * {@code org.springframework}: logic này không chạm CSDL, không cần transaction, và test được
 * bằng JUnit thuần không cần Testcontainers.
 *
 * <p><b>Điểm cốt lõi dễ làm sai — "ngày gốc không đổi".</b>
 * {@code LocalDate.plusMonths} của JDK tự làm tròn xuống ngày cuối tháng khi tháng đích không đủ
 * ngày ({@code 2026-01-31.plusMonths(1)} → {@code 2026-02-28}, không ném lỗi). Nhưng nó làm tròn
 * dựa trên con trỏ HIỆN TẠI, vốn có thể ĐÃ bị làm tròn ở kỳ trước. Cộng dồn liên tiếp từ con trỏ
 * đã tròn thì ngày sẽ "trôi" xuống 28 và ở lại đó vĩnh viễn: đặt ngày 31, qua tháng 2 một lần là
 * từ đó tháng nào cũng chạy ngày 28 — sai hoàn toàn ý định ban đầu.
 *
 * <p>Vì vậy mỗi lần tính đều ép lại theo {@code originalStartDate} qua {@link #applyOriginalDay}:
 * tháng 2 chạy ngày 28, nhưng tháng 3 quay lại đúng ngày 31.
 */
public final class RecurringDateCalculator {

    private RecurringDateCalculator() {}

    /**
     * @param originalStartDate ngày bắt đầu GỐC của khoản định kỳ — nguồn duy nhất của "ngày trong
     *     tháng" mong muốn. Không được truyền con trỏ hiện tại vào đây.
     * @param currentCursor kỳ vừa xử lý xong, mốc để cộng thêm một chu kỳ.
     * @param frequency {@code day} · {@code week} · {@code month} · {@code year}
     *     (ck_rec_frequency, V2).
     * @param interval số chu kỳ mỗi lần nhảy, {@code >= 1} (ck_rec_interval, V2).
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

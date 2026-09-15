package com.datn.financeapp.recurring.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datn.financeapp.recurring.util.RecurringDateCalculator;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Test JUnit thuần cho {@link RecurringDateCalculator} — không Spring, không Testcontainers, vì
 * đây là utility không chạm CSDL.
 *
 * <p>Ca quan trọng nhất là {@link #monthlyFrequency_afterShortMonth_returnsToOriginalDay31()}:
 * đúng điểm D-50/api/09 cảnh báo — sau khi tháng 2 làm tròn xuống ngày 28, kỳ tháng 3 phải QUAY
 * LẠI ngày 31 gốc chứ không kẹt ở 28 vĩnh viễn.
 */
class RecurringDateCalculatorTest {

    @Test
    void monthlyFrequency_day31_fallsBackToLastDayOfShortMonth() {
        LocalDate startDate = LocalDate.of(2026, 1, 31);

        LocalDate next = RecurringDateCalculator.nextRunDate(startDate, LocalDate.of(2026, 1, 31), "month", 1);

        // 2026 không phải năm nhuận nên tháng 2 chỉ có 28 ngày.
        assertThat(next).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void monthlyFrequency_afterShortMonth_returnsToOriginalDay31() {
        LocalDate startDate = LocalDate.of(2026, 1, 31);

        // Con trỏ đang ở ngày 28 (đã bị làm tròn ở kỳ trước) — nếu chỉ plusMonths thuần sẽ ra 28/03.
        LocalDate next = RecurringDateCalculator.nextRunDate(startDate, LocalDate.of(2026, 2, 28), "month", 1);

        assertThat(next).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void yearlyFrequency_leapDay_fallsBackInNonLeapYear() {
        LocalDate startDate = LocalDate.of(2024, 2, 29);

        LocalDate next = RecurringDateCalculator.nextRunDate(startDate, LocalDate.of(2024, 2, 29), "year", 1);

        assertThat(next).isEqualTo(LocalDate.of(2025, 2, 28));
    }

    @Test
    void dailyFrequency_addsExactInterval() {
        LocalDate startDate = LocalDate.of(2026, 1, 31);

        LocalDate next = RecurringDateCalculator.nextRunDate(startDate, LocalDate.of(2026, 1, 31), "day", 3);

        assertThat(next).isEqualTo(LocalDate.of(2026, 2, 3));
    }

    @Test
    void weeklyFrequency_addsExactInterval() {
        LocalDate startDate = LocalDate.of(2026, 3, 2);

        LocalDate next = RecurringDateCalculator.nextRunDate(startDate, LocalDate.of(2026, 3, 2), "week", 2);

        assertThat(next).isEqualTo(LocalDate.of(2026, 3, 16));
    }

    @Test
    void unknownFrequency_throwsIllegalArgumentException() {
        LocalDate startDate = LocalDate.of(2026, 1, 1);

        assertThatThrownBy(() ->
                        RecurringDateCalculator.nextRunDate(startDate, startDate, "quarter", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quarter");
    }
}

package com.datn.financeapp.module.budget.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datn.financeapp.common.exception.BusinessException;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PeriodTypeTest {

    @Test
    @DisplayName("Ngày cuối kỳ tháng là ngày áp chót trước mùng 1 tháng sau")
    void endOf_month() {
        assertThat(PeriodType.MONTH.endOf(LocalDate.of(2026, 10, 1))).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(PeriodType.MONTH.endOf(LocalDate.of(2026, 2, 1))).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    @DisplayName("Kỳ tuần dài đúng 7 ngày, kỳ quý 3 tháng, kỳ năm 12 tháng")
    void endOf_weekQuarterYear() {
        assertThat(PeriodType.WEEK.endOf(LocalDate.of(2026, 10, 5))).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(PeriodType.QUARTER.endOf(LocalDate.of(2026, 10, 1))).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(PeriodType.YEAR.endOf(LocalDate.of(2026, 1, 1))).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    @DisplayName("Đầu kỳ mặc định: tuần về thứ Hai, tháng/quý/năm về mùng 1")
    void startOfCurrent() {
        LocalDate wednesday = LocalDate.of(2026, 10, 14);

        assertThat(PeriodType.WEEK.startOfCurrent(wednesday)).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(PeriodType.MONTH.startOfCurrent(wednesday)).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(PeriodType.QUARTER.startOfCurrent(wednesday)).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(PeriodType.QUARTER.startOfCurrent(LocalDate.of(2026, 8, 20))).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(PeriodType.YEAR.startOfCurrent(wednesday)).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    @DisplayName("Đọc loại kỳ từ chuỗi JSON không phân biệt hoa thường, xuất ra chữ thường")
    void fromCode_caseInsensitive() {
        assertThat(PeriodType.fromCode("month")).isEqualTo(PeriodType.MONTH);
        assertThat(PeriodType.fromCode("QUARTER")).isEqualTo(PeriodType.QUARTER);
        assertThat(PeriodType.MONTH.code()).isEqualTo("month");
    }

    @Test
    @DisplayName("Chuỗi lạ không phải loại kỳ thì báo lỗi")
    void fromCode_unknown() {
        assertThatThrownBy(() -> PeriodType.fromCode("decade")).isInstanceOf(BusinessException.class);
    }
}

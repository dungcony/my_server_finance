package com.datn.financeapp.module.budget.enums;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;

/**
 * Loại kỳ của ngân sách. Trên JSON là chuỗi chữ thường ({@code week}, {@code month}, ...), trong
 * entity lưu theo tên enum.
 * <p>
 * <b>Các hàm:</b> {@link #startOfCurrent(LocalDate)} đầu kỳ chứa một ngày; {@link #endOf(LocalDate)}
 * ngày cuối kỳ khi biết ngày đầu; {@link #fromCode(String)} đọc chuỗi JSON không phân biệt hoa thường.
 * </p>
 */
public enum PeriodType {
    WEEK("week"),
    MONTH("month"),
    QUARTER("quarter"),
    YEAR("year");

    private static final int MONTHS_PER_QUARTER = 3;

    private final String code;

    PeriodType(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    /**
     * Đọc loại kỳ từ chuỗi, không phân biệt hoa thường.
     *
     * @param value chuỗi như {@code month} hoặc {@code MONTH}
     * @return loại kỳ tương ứng
     * @throws BusinessException nếu chuỗi không thuộc bốn loại kỳ
     */
    @JsonCreator
    public static PeriodType fromCode(String value) {
        for (PeriodType type : values()) {
            if (type.code.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Loại kỳ ngân sách không hợp lệ.");
    }

    /**
     * Ngày đầu của kỳ đang chứa {@code today}: thứ Hai của tuần, mùng 1 của tháng/quý/năm.
     */
    public LocalDate startOfCurrent(LocalDate today) {
        return switch (this) {
            case WEEK -> today.with(DayOfWeek.MONDAY);
            case MONTH -> today.withDayOfMonth(1);
            case QUARTER -> LocalDate.of(today.getYear(), firstMonthOfQuarter(today.getMonth()), 1);
            case YEAR -> LocalDate.of(today.getYear(), Month.JANUARY, 1);
        };
    }

    /**
     * Ngày cuối (tính cả ngày đó) của kỳ bắt đầu từ {@code start}.
     */
    public LocalDate endOf(LocalDate start) {
        return switch (this) {
            case WEEK -> start.plusDays(6);
            case MONTH -> start.plusMonths(1).minusDays(1);
            case QUARTER -> start.plusMonths(MONTHS_PER_QUARTER).minusDays(1);
            case YEAR -> start.plusYears(1).minusDays(1);
        };
    }

    private static Month firstMonthOfQuarter(Month month) {
        int firstMonthValue = ((month.getValue() - 1) / MONTHS_PER_QUARTER) * MONTHS_PER_QUARTER + 1;
        return Month.of(firstMonthValue);
    }
}

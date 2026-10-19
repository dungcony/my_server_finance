package com.datn.financeapp.module.budget.enums;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonCreator;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;

/**
 * Loại kỳ của ngân sách: WEEK, MONTH, QUARTER, YEAR.
 */
public enum PeriodType {
    WEEK,
    MONTH,
    QUARTER,
    YEAR;

    private static final int MONTHS_PER_QUARTER = 3;

    @JsonCreator
    public static PeriodType fromCode(String value) {
        if (value == null) {
            return null;
        }
        for (PeriodType type : values()) {
            if (type.name().equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Loại kỳ ngân sách không hợp lệ.");
    }

    public String code() {
        return name().toLowerCase();
    }

    public LocalDate startOfCurrent(LocalDate date) {
        return switch (this) {
            case WEEK -> date.with(DayOfWeek.MONDAY);
            case MONTH -> date.withDayOfMonth(1);
            case QUARTER -> LocalDate.of(date.getYear(), firstMonthOfQuarter(date.getMonth()), 1);
            case YEAR -> LocalDate.of(date.getYear(), Month.JANUARY, 1);
        };
    }

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

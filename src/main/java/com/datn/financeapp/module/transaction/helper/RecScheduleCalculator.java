package com.datn.financeapp.module.transaction.helper;

import com.datn.financeapp.module.transaction.enums.FrequencyType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Hỗ trợ tính toán mốc thời gian kế tiếp cho giao dịch định kỳ theo chu kỳ lịch thực tế.
 */
@Component
public class RecScheduleCalculator {

    public static final ZoneId ZONE_ID = ZoneId.of("Asia/Ho_Chi_Minh");

    /**
     * Tính toán ngày chạy tiếp theo từ ngày hiện tại.
     *
     * @param current   ngày hiện tại (LocalDate)
     * @param frequency loại tần suất lặp (DAY, WEEK, MONTH, QUARTER, YEAR)
     * @param interval  khoảng cách bước lặp
     * @return ngày kế tiếp dưới dạng {@link LocalDate}
     */
    public LocalDate nextOccurrence(LocalDate current, FrequencyType frequency, int interval) {
        return switch (frequency) {
            case DAY -> current.plusDays(interval);
            case WEEK -> current.plusWeeks(interval);
            case MONTH -> current.plusMonths(interval);
            case QUARTER -> current.plusMonths(interval * 3L);
            case YEAR -> current.plusYears(interval);
        };
    }
}

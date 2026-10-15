package com.datn.financeapp.module.transaction.helper;

import com.datn.financeapp.module.transaction.enums.FrequencyType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Hỗ trợ tính toán mốc thời gian kế tiếp cho giao dịch định kỳ theo chu kỳ lịch thực tế.
 */
@Component
public class RecScheduleCalculator {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Ho_Chi_Minh");

    /**
     * Tính toán mốc thời gian chạy tiếp theo từ mốc hiện tại.
     *
     * @param current   mốc thời gian hiện tại
     * @param frequency loại tần suất lặp (HOUR, DAY, WEEK, MONTH, QUARTER, YEAR)
     * @param interval  khoảng cách bước lặp
     * @return mốc thời gian kế tiếp dưới dạng {@link Instant}
     */
    public Instant nextOccurrence(Instant current, FrequencyType frequency, int interval) {
        ZonedDateTime zdt = current.atZone(ZONE_ID);
        ZonedDateTime nextZdt = switch (frequency) {
            case HOUR -> zdt.plusHours(interval);
            case DAY -> zdt.plusDays(interval);
            case WEEK -> zdt.plusWeeks(interval);
            case MONTH -> zdt.plusMonths(interval);
            case QUARTER -> zdt.plusMonths(interval * 3L);
            case YEAR -> zdt.plusYears(interval);
        };
        return nextZdt.toInstant();
    }
}

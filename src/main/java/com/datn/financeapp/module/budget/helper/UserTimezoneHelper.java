package com.datn.financeapp.module.budget.helper;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Tiện ích xác định múi giờ của người dùng để đảm bảo kỳ ngân sách (LocalDate)
 * và giao dịch (Instant UTC) luôn đồng bộ chính xác dù người dùng ở Việt Nam, Mỹ hay bất kỳ đâu.
 */
@Slf4j
@Component
public class UserTimezoneHelper {

    public static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String HEADER_TIMEZONE = "X-Timezone";

    /**
     * Lấy múi giờ hiện tại của người dùng từ HTTP Header "X-Timezone".
     * Nếu không có header hoặc không hợp lệ, trả về múi giờ mặc định (Asia/Ho_Chi_Minh).
     */
    public ZoneId getCurrentZone() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return DEFAULT_ZONE;
        }
        HttpServletRequest req = attrs.getRequest();
        String tzHeader = req.getHeader(HEADER_TIMEZONE);
        if (tzHeader == null || tzHeader.isBlank()) {
            return DEFAULT_ZONE;
        }
        try {
            return ZoneId.of(tzHeader.trim());
        } catch (Exception e) {
            log.warn("Múi giờ không hợp lệ từ header X-Timezone: '{}', fallback về {}", tzHeader, DEFAULT_ZONE);
            return DEFAULT_ZONE;
        }
    }

    /**
     * Chuyển mốc thời gian giao dịch (Instant UTC) sang ngày địa phương (LocalDate) của người dùng.
     */
    public LocalDate toLocalDate(Instant instant, ZoneId zone) {
        if (instant == null) {
            return LocalDate.now(zone != null ? zone : DEFAULT_ZONE);
        }
        return instant.atZone(zone != null ? zone : DEFAULT_ZONE).toLocalDate();
    }

    /**
     * Chuyển ngày đầu kỳ (00:00:00 theo giờ user) sang mốc UTC Instant để query CSDL bảng transactions.
     */
    public Instant toStartInstant(LocalDate date, ZoneId zone) {
        ZoneId z = zone != null ? zone : DEFAULT_ZONE;
        return date.atStartOfDay(z).toInstant();
    }

    /**
     * Chuyển ngày cuối kỳ (23:59:59.999999999 theo giờ user) sang mốc UTC Instant để query CSDL bảng transactions.
     */
    public Instant toEndInstant(LocalDate date, ZoneId zone) {
        ZoneId z = zone != null ? zone : DEFAULT_ZONE;
        return date.plusDays(1).atStartOfDay(z).toInstant().minusNanos(1);
    }
}

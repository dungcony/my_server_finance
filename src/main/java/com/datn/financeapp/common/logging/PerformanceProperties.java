package com.datn.financeapp.common.logging;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Ngưỡng cảnh báo của {@link ServicePerformanceAspect}.
 *
 * @param sqlWarnThreshold một lần gọi service bắn nhiều hơn số câu SQL này thì ghi WARN (nghi N+1)
 * @param slowMs           một lần gọi service chạy lâu hơn số mili giây này thì ghi WARN (chậm)
 */
@ConfigurationProperties(prefix = "app.perf")
public record PerformanceProperties(
        @DefaultValue("10") int sqlWarnThreshold,
        @DefaultValue("300") long slowMs) {
}

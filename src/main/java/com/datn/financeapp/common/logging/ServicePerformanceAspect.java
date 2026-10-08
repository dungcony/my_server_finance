package com.datn.financeapp.common.logging;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Đo thời gian và số câu SQL của từng method public trong các {@code *ServiceImpl}, chỉ bật ở
 * profile {@code dev}. Mỗi lần gọi ghi một dòng {@code [PERF]}; vượt ngưỡng trong
 * {@link PerformanceProperties} thì nâng lên WARN để dễ thấy method nào chậm hoặc nghi N+1.
 * <p>
 * Số câu SQL được đo riêng biệt cho từng luồng qua {@link SqlStatementInspector}, đảm bảo không
 * bị cộng dồn chéo giữa các request khi chạy đồng thời hoặc test tải.
 * </p>
 * <p>
 * Các hàm chính trong class:
 * <ul>
 *   <li>{@link #measure(ProceedingJoinPoint)}: Đo thời gian thực thi và số lượng câu lệnh SQL của method service.</li>
 * </ul>
 * </p>
 *
 * Đặt order cao nhất để aspect nằm ngoài {@code @Transactional}, đếm được cả câu SQL phát sinh lúc
 * flush/commit.
 */
@Slf4j
@Aspect
@Component
@Profile("dev")
@Order(Ordered.HIGHEST_PRECEDENCE)
@EnableConfigurationProperties(PerformanceProperties.class)
public class ServicePerformanceAspect {

    private final SqlStatementInspector statementInspector;
    private final PerformanceProperties properties;

    public ServicePerformanceAspect(SqlStatementInspector statementInspector, PerformanceProperties properties) {
        this.statementInspector = statementInspector;
        this.properties = properties;
    }

    @Around("execution(public * com.datn.financeapp..service.impl.*.*(..))")
    public Object measure(ProceedingJoinPoint pjp) throws Throwable {
        statementInspector.enter();
        long sqlBefore = statementInspector.getCount();
        long start = System.nanoTime();
        try {
            return pjp.proceed();
        } finally {
            long sqlCount = statementInspector.getCount() - sqlBefore;
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            statementInspector.exit();
            report(pjp, sqlCount, elapsedMs);
        }
    }

    private void report(ProceedingJoinPoint pjp, long sqlCount, long elapsedMs) {
        String method = pjp.getTarget().getClass().getSimpleName() + "." + pjp.getSignature().getName();
        boolean tooManySql = sqlCount > properties.sqlWarnThreshold();
        boolean tooSlow = elapsedMs > properties.slowMs();

        String line = "[PERF] %s | %dms | %d SQL".formatted(method, elapsedMs, sqlCount);
        if (tooManySql)
            line += " | NGHI N+1: vượt " + properties.sqlWarnThreshold() + " câu SQL";
        if (tooSlow)
            line += " | CHẬM: vượt " + properties.slowMs() + "ms";

        if (tooManySql || tooSlow)
            log.warn(line);
        else
            log.info(line);
    }
}

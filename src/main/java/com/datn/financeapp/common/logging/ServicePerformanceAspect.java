package com.datn.financeapp.common.logging;

import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Đo thời gian và số câu SQL của từng method public trong các {@code *ServiceImpl}, chỉ bật ở
 * profile {@code dev}. Mỗi lần gọi ghi một dòng {@code [PERF]}; vượt ngưỡng trong
 * {@link PerformanceProperties} thì nâng lên WARN để dễ thấy method nào chậm hoặc nghi N+1.
 *
 * Số câu SQL là hiệu của bộ đếm Hibernate trước và sau khi chạy, nên nếu nhiều request chạy song
 * song thì con số có thể lẫn câu của request khác. Method service gọi method service khác thì mỗi
 * method đều có dòng log riêng và con số của method ngoài đã gồm cả method trong.
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

    private final Statistics statistics;
    private final PerformanceProperties properties;

    public ServicePerformanceAspect(EntityManagerFactory entityManagerFactory, PerformanceProperties properties) {
        this.statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        this.statistics.setStatisticsEnabled(true);
        this.properties = properties;
    }

    @Around("execution(public * com.datn.financeapp..service.impl.*.*(..))")
    public Object measure(ProceedingJoinPoint pjp) throws Throwable {
        long sqlBefore = statistics.getPrepareStatementCount();
        long start = System.nanoTime();
        try {
            return pjp.proceed();
        } finally {
            long sqlCount = statistics.getPrepareStatementCount() - sqlBefore;
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
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

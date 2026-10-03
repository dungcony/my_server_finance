package com.datn.financeapp.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.persistence.EntityManagerFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServicePerformanceAspectTest {

    private static final int SQL_THRESHOLD = 10;
    private static final long SLOW_MS = 300;

    @Mock
    private EntityManagerFactory entityManagerFactory;

    @Mock
    private SessionFactory sessionFactory;

    @Mock
    private Statistics statistics;

    @Mock
    private ProceedingJoinPoint pjp;

    private ServicePerformanceAspect aspect;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger aspectLogger;

    @BeforeEach
    void setUp() {
        when(entityManagerFactory.unwrap(SessionFactory.class)).thenReturn(sessionFactory);
        when(sessionFactory.getStatistics()).thenReturn(statistics);
        aspect = new ServicePerformanceAspect(entityManagerFactory, new PerformanceProperties(SQL_THRESHOLD, SLOW_MS));

        aspectLogger = (Logger) LoggerFactory.getLogger(ServicePerformanceAspect.class);
        aspectLogger.setLevel(Level.DEBUG);
        logAppender = new ListAppender<>();
        logAppender.start();
        aspectLogger.addAppender(logAppender);
    }

    @AfterEach
    void detachAppender() {
        aspectLogger.detachAppender(logAppender);
    }

    @Test
    @DisplayName("Khởi tạo: bật thống kê Hibernate để có bộ đếm SQL")
    void constructor_EnablesHibernateStatistics() {
        verify(statistics).setStatisticsEnabled(true);
    }

    @Test
    @DisplayName("Method chạy bình thường -> trả nguyên kết quả của method gốc")
    void measure_ReturnsOriginalResult() throws Throwable {
        stubTarget();
        when(pjp.proceed()).thenReturn("ket-qua");
        when(statistics.getPrepareStatementCount()).thenReturn(0L, 0L);

        Object result = aspect.measure(pjp);

        assertThat(result).isEqualTo("ket-qua");
    }

    @Test
    @DisplayName("Method ném exception -> ném lại nguyên exception và vẫn ghi log")
    void measure_RethrowsExceptionAndStillLogs() throws Throwable {
        stubTarget();
        IllegalStateException boom = new IllegalStateException("loi nghiep vu");
        when(pjp.proceed()).thenThrow(boom);
        when(statistics.getPrepareStatementCount()).thenReturn(0L, 3L);

        assertThatThrownBy(() -> aspect.measure(pjp)).isSameAs(boom);

        assertThat(logAppender.list).hasSize(1);
        assertThat(logAppender.list.get(0).getFormattedMessage()).contains("FakeServiceImpl.load", "3 SQL");
    }

    @Test
    @DisplayName("Ít câu SQL và nhanh -> ghi INFO, không cảnh báo")
    void measure_UnderThresholds_LogsInfo() throws Throwable {
        stubTarget();
        when(pjp.proceed()).thenReturn(null);
        when(statistics.getPrepareStatementCount()).thenReturn(5L, 8L);

        aspect.measure(pjp);

        assertThat(logAppender.list).hasSize(1);
        ILoggingEvent event = logAppender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage())
                .contains("[PERF]", "FakeServiceImpl.load", "3 SQL")
                .doesNotContain("N+1");
    }

    @Test
    @DisplayName("Số câu SQL đúng bằng ngưỡng -> chưa cảnh báo")
    void measure_SqlCountEqualsThreshold_LogsInfo() throws Throwable {
        stubTarget();
        when(pjp.proceed()).thenReturn(null);
        when(statistics.getPrepareStatementCount()).thenReturn(0L, (long) SQL_THRESHOLD);

        aspect.measure(pjp);

        assertThat(logAppender.list.get(0).getLevel()).isEqualTo(Level.INFO);
    }

    @Test
    @DisplayName("Vượt ngưỡng số câu SQL -> ghi WARN nghi N+1 kèm số câu thực tế")
    void measure_OverSqlThreshold_LogsWarnSuspectNPlusOne() throws Throwable {
        stubTarget();
        when(pjp.proceed()).thenReturn(null);
        when(statistics.getPrepareStatementCount()).thenReturn(100L, 123L);

        aspect.measure(pjp);

        ILoggingEvent event = logAppender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).contains("FakeServiceImpl.load", "23 SQL", "N+1");
    }

    @Test
    @DisplayName("Chạy lâu hơn ngưỡng mili giây -> ghi WARN chậm")
    void measure_OverSlowThreshold_LogsWarnSlow() throws Throwable {
        stubTarget();
        when(pjp.proceed()).thenAnswer(inv -> {
            Thread.sleep(SLOW_MS + 50);
            return null;
        });
        when(statistics.getPrepareStatementCount()).thenReturn(0L, 1L);

        aspect.measure(pjp);

        ILoggingEvent event = logAppender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).contains("CHẬM").doesNotContain("N+1");
    }

    private void stubTarget() {
        when(pjp.getTarget()).thenReturn(new FakeServiceImpl());
        when(pjp.getSignature()).thenReturn(new FakeSignature("load"));
    }

    static class FakeServiceImpl {
    }

    // chỉ cần tên method, các phần còn lại của Signature không dùng tới
    private record FakeSignature(String name) implements org.aspectj.lang.Signature {
        @Override
        public String toShortString() {
            return name;
        }

        @Override
        public String toLongString() {
            return name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public int getModifiers() {
            return 0;
        }

        @Override
        public Class<?> getDeclaringType() {
            return FakeServiceImpl.class;
        }

        @Override
        public String getDeclaringTypeName() {
            return FakeServiceImpl.class.getName();
        }
    }
}

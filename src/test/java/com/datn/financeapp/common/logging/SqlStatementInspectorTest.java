package com.datn.financeapp.common.logging;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SqlStatementInspectorTest {

    private SqlStatementInspector inspector;

    @BeforeEach
    void setUp() {
        inspector = new SqlStatementInspector();
    }

    @AfterEach
    void tearDown() {
        inspector.exit();
    }

    @Test
    @DisplayName("Chưa enter -> không đếm câu SQL (bỏ qua SQL ngoài service)")
    void inspect_WithoutEnter_DoesNotCount() {
        inspector.inspect("SELECT 1");

        assertThat(inspector.getCount()).isZero();
    }

    @Test
    @DisplayName("Đã enter -> đếm đúng số câu SQL được inspect")
    void inspect_AfterEnter_CountsStatements() {
        inspector.enter();

        inspector.inspect("SELECT 1");
        inspector.inspect("INSERT INTO fake_table VALUES (1)");

        assertThat(inspector.getCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Method lồng nhau -> method con kết thúc không làm mất biến đếm của method cha")
    void nestedCalls_MaintainsCounterUntilOutermostExit() {
        // method cha bắt đầu
        inspector.enter();
        inspector.inspect("SELECT 1");
        long sqlBeforeChild = inspector.getCount();

        // method con bắt đầu
        inspector.enter();
        inspector.inspect("SELECT 2");
        inspector.inspect("SELECT 3");
        long sqlChild = inspector.getCount() - sqlBeforeChild;
        inspector.exit();

        assertThat(sqlChild).isEqualTo(2);
        assertThat(inspector.getCount()).isEqualTo(3);

        // method cha kết thúc
        inspector.exit();
        assertThat(inspector.getCount()).isZero();
    }

    @Test
    @DisplayName("Đa luồng song song -> biến đếm của luồng này hoàn toàn độc lập với luồng khác")
    void concurrentThreads_IsolateCounters() throws InterruptedException {
        inspector.enter();
        inspector.inspect("SELECT * FROM parent");

        AtomicLong thread2Count = new AtomicLong();
        CountDownLatch latch = new CountDownLatch(1);

        Thread otherThread = new Thread(() -> {
            inspector.enter();
            inspector.inspect("SELECT * FROM child_1");
            inspector.inspect("SELECT * FROM child_2");
            thread2Count.set(inspector.getCount());
            inspector.exit();
            latch.countDown();
        });

        otherThread.start();
        boolean finished = latch.await(2, TimeUnit.SECONDS);

        assertThat(finished).isTrue();
        assertThat(thread2Count.get()).isEqualTo(2);
        // luồng chính vẫn chỉ có đúng 1 câu SQL ban đầu
        assertThat(inspector.getCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Cấu hình Spring Boot: đăng ký inspector vào Hibernate properties")
    void customize_RegistersInspectorInHibernateProperties() {
        Map<String, Object> props = new HashMap<>();

        inspector.customize(props);

        assertThat(props).containsEntry(AvailableSettings.STATEMENT_INSPECTOR, inspector);
    }
}

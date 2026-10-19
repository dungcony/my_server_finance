package com.datn.financeapp.unitTest.pay.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datn.financeapp.module.pay.config.PaymentWebhookLogProperties;
import com.datn.financeapp.module.pay.repository.PaymentWebhookLogRepository;
import com.datn.financeapp.module.pay.scheduler.PaymentWebhookLogCleanupJob;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentWebhookLogCleanupJobTest {

    private static final Instant NOW = Instant.parse("2026-04-20T10:00:00Z");

    @Mock
    private PaymentWebhookLogRepository repository;

    private PaymentWebhookLogCleanupJob job;

    @BeforeEach
    void setUp() {
        job = new PaymentWebhookLogCleanupJob(repository, new PaymentWebhookLogProperties(30, 10),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("cleanup: chỉ xoá bản ghi cũ hơn số ngày giữ log cấu hình (30 ngày)")
    void cleanup_DeletesOlderThanRetention() {
        when(repository.deleteOlderThan(any(Instant.class))).thenReturn(5);

        job.cleanup();

        ArgumentCaptor<Instant> threshold = ArgumentCaptor.forClass(Instant.class);
        verify(repository).deleteOlderThan(threshold.capture());
        assertThat(threshold.getValue()).isEqualTo(NOW.minusSeconds(30L * 24 * 3600));
    }

    @Test
    @DisplayName("cleanup: lỗi DB chỉ ghi log, không ném ra ngoài (không làm hỏng scheduler)")
    void cleanup_RepositoryFails_Swallowed() {
        when(repository.deleteOlderThan(any(Instant.class))).thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> job.cleanup()).doesNotThrowAnyException();
    }
}

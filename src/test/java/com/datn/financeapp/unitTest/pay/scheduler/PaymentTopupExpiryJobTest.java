package com.datn.financeapp.unitTest.pay.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datn.financeapp.module.pay.config.PaymentTopupProperties;
import com.datn.financeapp.module.pay.scheduler.PaymentTopupExpiryJob;
import com.datn.financeapp.module.pay.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class PaymentTopupExpiryJobTest {

    private static final Instant NOW = Instant.parse("2026-10-21T08:00:00Z");

    @Mock
    private PaymentService paymentService;

    private PaymentTopupProperties topupProperties;
    private PaymentTopupExpiryJob job;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        topupProperties = new PaymentTopupProperties(86400, 3600, 60);
        job = new PaymentTopupExpiryJob(paymentService, topupProperties, clock);
    }

    @Test
    @DisplayName("quét và phạt các đơn PARTIALLY_PAID quá hạn bù tiền; nếu một đơn lỗi thì không dừng các đơn sau")
    void processTopupDeadlines_ForfeitsOverdueOrders() {
        when(paymentService.findOverdueTopupOrderCodes(any(Instant.class), anyInt()))
                .thenReturn(List.of(100L, 200L, 300L));
        doThrow(new RuntimeException("Lỗi kết nối payOS")).when(paymentService).forfeitIfTopupOverdue(200L);

        when(paymentService.findTopupReminderCandidates(any(Instant.class), any(Instant.class), anyInt()))
                .thenReturn(List.of());

        job.processTopupDeadlines();

        verify(paymentService).forfeitIfTopupOverdue(100L);
        verify(paymentService).forfeitIfTopupOverdue(200L);
        verify(paymentService).forfeitIfTopupOverdue(300L);
    }

    @Test
    @DisplayName("gửi thông báo nhắc nhở cho các đơn PARTIALLY_PAID sắp đến hạn chót")
    void processTopupDeadlines_RemindsCandidates() {
        when(paymentService.findOverdueTopupOrderCodes(any(Instant.class), anyInt()))
                .thenReturn(List.of());

        Instant threshold = NOW.plusSeconds(3600);
        when(paymentService.findTopupReminderCandidates(NOW, threshold, 50))
                .thenReturn(List.of(500L, 600L));

        job.processTopupDeadlines();

        verify(paymentService).remindTopupIfCandidate(500L, NOW, threshold);
        verify(paymentService).remindTopupIfCandidate(600L, NOW, threshold);
    }
}

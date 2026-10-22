package com.datn.financeapp.unitTest.pay.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datn.financeapp.module.pay.scheduler.PaymentExpiryJob;
import com.datn.financeapp.module.pay.service.PaymentService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentExpiryJobTest {

    private static final Instant NOW = Instant.parse("2026-04-20T10:00:00Z");

    @Mock
    private PaymentService paymentService;

    private PaymentExpiryJob job;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        job = new PaymentExpiryJob(paymentService, clock);
    }

    @Test
    @DisplayName("một đơn ném lỗi giữa danh sách -> các đơn sau vẫn được xử lý")
    void expireOverduePayments_OneFailureDoesNotStopOthers() {
        when(paymentService.findOverdueOrderCodes(any(Instant.class), anyInt())).thenReturn(List.of(1L, 2L, 3L));
        doThrow(new RuntimeException("payOS down")).when(paymentService).expireIfOverdue(2L);

        job.expireOverduePayments();

        verify(paymentService).expireIfOverdue(1L);
        verify(paymentService).expireIfOverdue(2L);
        verify(paymentService).expireIfOverdue(3L);
    }

    @Test
    @DisplayName("không có đơn quá hạn -> không xử lý gì")
    void expireOverduePayments_NothingOverdue() {
        when(paymentService.findOverdueOrderCodes(any(Instant.class), anyInt())).thenReturn(List.of());

        job.expireOverduePayments();

        verify(paymentService, never()).expireIfOverdue(anyLong());
    }
}

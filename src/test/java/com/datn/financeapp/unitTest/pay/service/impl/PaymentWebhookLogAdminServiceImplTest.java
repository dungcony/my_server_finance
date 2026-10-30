package com.datn.financeapp.unitTest.pay.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datn.financeapp.module.pay.config.PaymentWebhookLogProperties;
import com.datn.financeapp.module.pay.dto.res.WebhookLogListRes;
import com.datn.financeapp.module.pay.dto.res.WebhookSummaryRes;
import com.datn.financeapp.module.pay.entity.PaymentWebhookLog;
import com.datn.financeapp.module.pay.enums.WebhookResult;
import com.datn.financeapp.module.pay.mapper.PaymentWebhookLogMapperImpl;
import com.datn.financeapp.module.pay.repository.PaymentWebhookLogRepository;
import com.datn.financeapp.module.pay.service.impl.PaymentWebhookLogAdminServiceImpl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class PaymentWebhookLogAdminServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-04-20T10:00:00Z");

    @Mock
    private PaymentWebhookLogRepository repository;

    private PaymentWebhookLogAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PaymentWebhookLogAdminServiceImpl(repository, new PaymentWebhookLogMapperImpl(),
                new PaymentWebhookLogProperties(30, 10), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private record Count(WebhookResult result, Long total) implements PaymentWebhookLogRepository.ResultCount {
        @Override
        public WebhookResult getResult() {
            return result;
        }

        @Override
        public Long getTotal() {
            return total;
        }
    }

    @Test
    @DisplayName("summary: tổng, số lỗi, tỉ lệ lỗi và đếm theo từng loại kết quả")
    void summary_CountsAndFailureRate() {
        when(repository.countGroupedByResult(any(Instant.class))).thenReturn(List.of(
                new Count(WebhookResult.PROCESSED, 6L),
                new Count(WebhookResult.ALREADY_PAID, 2L),
                new Count(WebhookResult.INVALID_SIGNATURE, 1L),
                new Count(WebhookResult.UNDERPAID, 1L)));
        when(repository.lastReceivedAt(anyCollection())).thenReturn(NOW.minusSeconds(60));
        when(repository.findPaidWithoutWebhook(any(), any(), anyCollection(), any(Pageable.class))).thenReturn(List.of());

        WebhookSummaryRes res = service.summary(24);

        assertThat(res.total()).isEqualTo(10);
        assertThat(res.failed()).isEqualTo(2);
        assertThat(res.failureRate()).isEqualTo(0.2);
        assertThat(res.countsByResult()).containsEntry(WebhookResult.PROCESSED, 6L)
                .containsEntry(WebhookResult.UNDERPAID, 1L);
        assertThat(res.hours()).isEqualTo(24);
    }

    @Test
    @DisplayName("summary: chưa có webhook nào -> tỉ lệ lỗi 0, không chia cho 0")
    void summary_NoData_ZeroRate() {
        when(repository.countGroupedByResult(any(Instant.class))).thenReturn(List.of());
        when(repository.lastReceivedAt(anyCollection())).thenReturn(null);
        when(repository.findPaidWithoutWebhook(any(), any(), anyCollection(), any(Pageable.class))).thenReturn(List.of());

        WebhookSummaryRes res = service.summary(24);

        assertThat(res.total()).isZero();
        assertThat(res.failureRate()).isZero();
        assertThat(res.lastSuccessAt()).isNull();
        assertThat(res.lastFailureAt()).isNull();
    }

    @Test
    @DisplayName("summary: số giờ ngoài 1..168 bị đưa về mốc gần nhất")
    void summary_HoursClamped() {
        when(repository.countGroupedByResult(any(Instant.class))).thenReturn(List.of());
        when(repository.findPaidWithoutWebhook(any(), any(), anyCollection(), any(Pageable.class))).thenReturn(List.of());

        assertThat(service.summary(0).hours()).isEqualTo(1);
        assertThat(service.summary(100_000).hours()).isEqualTo(168);
        assertThat(service.summary(-5).hours()).isEqualTo(1);
    }

    @Test
    @DisplayName("summary: đơn PAID quá ngưỡng mà không có webhook -> liệt kê; ngưỡng lấy từ cấu hình (10 phút)")
    void summary_PaidWithoutWebhook_UsesConfiguredGrace() {
        when(repository.countGroupedByResult(any(Instant.class))).thenReturn(List.of());
        when(repository.findPaidWithoutWebhook(any(), any(), anyCollection(), any(Pageable.class)))
                .thenReturn(List.of(200_005L, 200_003L));

        WebhookSummaryRes res = service.summary(24);

        assertThat(res.paidWithoutWebhook()).containsExactly(200_005L, 200_003L);
        assertThat(res.missingMinutes()).isEqualTo(10);
        ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> paidBefore = ArgumentCaptor.forClass(Instant.class);
        verify(repository).findPaidWithoutWebhook(since.capture(), paidBefore.capture(), anyCollection(), any(Pageable.class));
        assertThat(since.getValue()).isEqualTo(NOW.minusSeconds(24 * 3600L));
        assertThat(paidBefore.getValue()).isEqualTo(NOW.minusSeconds(10 * 60L));
    }

    @Test
    @DisplayName("list: map đủ trường và meta phân trang 1-indexed")
    @SuppressWarnings("unchecked")
    void list_MapsRowsAndMeta() {
        PaymentWebhookLog row = PaymentWebhookLog.builder()
                .result(WebhookResult.ORDER_NOT_FOUND).orderCode(200_009L).amount(49_000L).httpStatus(200)
                .durationMs(5).occurrences(1).receivedAt(NOW).message("m").build();
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));

        WebhookLogListRes res = service.list(null, true, null, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 20),
                PageRequest.of(0, 20));

        assertThat(res.items()).hasSize(1);
        assertThat(res.items().get(0).result()).isEqualTo(WebhookResult.ORDER_NOT_FOUND);
        assertThat(res.items().get(0).orderCode()).isEqualTo(200_009L);
        assertThat(res.meta().page()).isEqualTo(1);
        assertThat(res.meta().totalItems()).isEqualTo(1);
    }

    @Test
    @DisplayName("list: luôn mới nhất trước, bỏ qua sort client gửi lên")
    @SuppressWarnings("unchecked")
    void list_AlwaysNewestFirst() {
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.list(null, false, null, null, null,
                PageRequest.of(1, 10, org.springframework.data.domain.Sort.by("durationMs")));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getSort().getOrderFor("receivedAt")).isNotNull();
        assertThat(pageable.getValue().getSort().getOrderFor("durationMs")).isNull();
    }
}

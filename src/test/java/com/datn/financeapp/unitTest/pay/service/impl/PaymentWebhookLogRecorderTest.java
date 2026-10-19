package com.datn.financeapp.unitTest.pay.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datn.financeapp.module.pay.dto.res.WebhookOutcome;
import com.datn.financeapp.module.pay.entity.PaymentWebhookLog;
import com.datn.financeapp.module.pay.enums.WebhookResult;
import com.datn.financeapp.module.pay.repository.PaymentWebhookLogRepository;
import com.datn.financeapp.module.pay.service.impl.PaymentWebhookLogRecorderImpl;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentWebhookLogRecorderTest {

    private static final Instant NOW = Instant.parse("2026-04-20T10:00:30Z");
    private static final Instant MINUTE_START = Instant.parse("2026-04-20T10:00:00Z");

    @Mock
    private PaymentWebhookLogRepository repository;

    private PaymentWebhookLogRecorderImpl recorder;

    @BeforeEach
    void setUp() {
        recorder = new PaymentWebhookLogRecorderImpl(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private WebhookOutcome outcome(WebhookResult result, String message) {
        return new WebhookOutcome(result, 200_001L, "link-1", 49_000L, "00", true, message);
    }

    @Test
    @DisplayName("record: lưu đủ trường, thời điểm theo Clock, occurrences = 1, không gộp")
    void record_NormalOutcome_SavesAllFields() {
        recorder.record(outcome(WebhookResult.PROCESSED, "ok"), 200, 12L, "203.0.113.7");

        ArgumentCaptor<PaymentWebhookLog> saved = ArgumentCaptor.forClass(PaymentWebhookLog.class);
        verify(repository).save(saved.capture());
        PaymentWebhookLog row = saved.getValue();
        assertThat(row.getResult()).isEqualTo(WebhookResult.PROCESSED);
        assertThat(row.getOrderCode()).isEqualTo(200_001L);
        assertThat(row.getPaymentLinkId()).isEqualTo("link-1");
        assertThat(row.getAmount()).isEqualTo(49_000L);
        assertThat(row.getPayosCode()).isEqualTo("00");
        assertThat(row.getSuccess()).isTrue();
        assertThat(row.getMessage()).isEqualTo("ok");
        assertThat(row.getHttpStatus()).isEqualTo(200);
        assertThat(row.getDurationMs()).isEqualTo(12);
        assertThat(row.getClientIp()).isEqualTo("203.0.113.7");
        assertThat(row.getReceivedAt()).isEqualTo(NOW);
        assertThat(row.getOccurrences()).isEqualTo(1);
        assertThat(row.isAggregated()).isFalse();
    }

    @Test
    @DisplayName("record: thông điệp dài hơn 500 ký tự bị cắt, không làm lỗi khi ghi")
    void record_LongMessage_Truncated() {
        recorder.record(outcome(WebhookResult.ERROR, "x".repeat(2_000)), 500, 1L, "ip");

        ArgumentCaptor<PaymentWebhookLog> saved = ArgumentCaptor.forClass(PaymentWebhookLog.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getMessage()).hasSize(500);
    }

    @Test
    @DisplayName("record: kết quả không phải giả mạo (VD ORDER_NOT_FOUND) không bao giờ bị giới hạn/đếm")
    void record_NonSpamResult_NeverRateLimited() {
        recorder.record(outcome(WebhookResult.ORDER_NOT_FOUND, "x"), 200, 1L, "ip");

        verify(repository, never()).countByResultInAndAggregatedFalseAndReceivedAtGreaterThanEqual(anyCollection(), any());
        verify(repository).save(any(PaymentWebhookLog.class));
    }

    @Test
    @DisplayName("record: INVALID_SIGNATURE dưới giới hạn 100 dòng/phút -> ghi bình thường")
    void record_InvalidSignature_UnderLimit_SavedNormally() {
        when(repository.countByResultInAndAggregatedFalseAndReceivedAtGreaterThanEqual(anyCollection(), eq(MINUTE_START)))
                .thenReturn(99L);

        recorder.record(outcome(WebhookResult.INVALID_SIGNATURE, "sig"), 400, 1L, "ip");

        ArgumentCaptor<PaymentWebhookLog> saved = ArgumentCaptor.forClass(PaymentWebhookLog.class);
        verify(repository, times(1)).save(saved.capture());
        assertThat(saved.getValue().isAggregated()).isFalse();
    }

    @Test
    @DisplayName("record: INVALID_SIGNATURE vượt 100 dòng/phút, chưa có dòng gộp -> tạo một dòng gộp (occurrences = 1), không mất dữ liệu")
    void record_InvalidSignature_OverLimit_CreatesAggregateRow() {
        when(repository.countByResultInAndAggregatedFalseAndReceivedAtGreaterThanEqual(anyCollection(), eq(MINUTE_START)))
                .thenReturn(100L);
        when(repository.findFirstByResultAndAggregatedTrueAndReceivedAtGreaterThanEqual(
                WebhookResult.INVALID_SIGNATURE, MINUTE_START)).thenReturn(Optional.empty());

        recorder.record(outcome(WebhookResult.INVALID_SIGNATURE, "sig"), 400, 1L, "ip");

        ArgumentCaptor<PaymentWebhookLog> saved = ArgumentCaptor.forClass(PaymentWebhookLog.class);
        verify(repository, times(1)).save(saved.capture());
        assertThat(saved.getValue().isAggregated()).isTrue();
        assertThat(saved.getValue().getOccurrences()).isEqualTo(1);
        assertThat(saved.getValue().getResult()).isEqualTo(WebhookResult.INVALID_SIGNATURE);
        assertThat(saved.getValue().getMessage()).contains("gộp");
    }

    @Test
    @DisplayName("record: vượt giới hạn và đã có dòng gộp -> cộng dồn occurrences vào dòng đó, không thêm dòng mới")
    void record_InvalidBody_OverLimit_IncrementsExistingAggregate() {
        PaymentWebhookLog existing = PaymentWebhookLog.builder()
                .result(WebhookResult.INVALID_BODY).aggregated(true).occurrences(7).receivedAt(MINUTE_START).build();
        when(repository.countByResultInAndAggregatedFalseAndReceivedAtGreaterThanEqual(anyCollection(), eq(MINUTE_START)))
                .thenReturn(250L);
        when(repository.findFirstByResultAndAggregatedTrueAndReceivedAtGreaterThanEqual(
                WebhookResult.INVALID_BODY, MINUTE_START)).thenReturn(Optional.of(existing));

        recorder.record(outcome(WebhookResult.INVALID_BODY, "body"), 400, 1L, "ip");

        verify(repository).save(existing);
        assertThat(existing.getOccurrences()).isEqualTo(8);
        assertThat(existing.getMessage()).contains("8");
    }

    @Test
    @DisplayName("record: lỗi khi ghi DB không ném ra ngoài (không được làm hỏng phản hồi webhook)")
    void record_RepositoryFails_DoesNotThrow() {
        when(repository.save(any(PaymentWebhookLog.class))).thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> recorder.record(outcome(WebhookResult.PROCESSED, "ok"), 200, 1L, "ip"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("record: outcome null (phòng thủ) -> bỏ qua, không ghi, không ném lỗi")
    void record_NullOutcome_Ignored() {
        assertThatCode(() -> recorder.record(null, 200, 1L, "ip")).doesNotThrowAnyException();

        verify(repository, never()).save(any(PaymentWebhookLog.class));
    }
}

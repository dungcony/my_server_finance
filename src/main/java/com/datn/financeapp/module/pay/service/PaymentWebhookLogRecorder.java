package com.datn.financeapp.module.pay.service;

import com.datn.financeapp.module.pay.dto.res.WebhookOutcome;

/**
 * Ghi nhật ký một lần payOS gọi webhook. Chạy trong transaction riêng để vẫn lưu khi xử lý nghiệp vụ bị rollback,
 * và không bao giờ ném lỗi (lỗi ghi log không được làm hỏng phản hồi webhook).
 */
public interface PaymentWebhookLogRecorder {

    void record(WebhookOutcome outcome, int httpStatus, long durationMs, String clientIp);
}

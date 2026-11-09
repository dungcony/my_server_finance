package com.datn.financeapp.module.pay.service;

/**
 * Điểm vào duy nhất cho webhook payOS: parse body, xử lý nghiệp vụ, ghi nhật ký cho dashboard admin,
 * rồi ném lại đúng lỗi gốc (phản hồi HTTP của webhook không đổi so với trước khi có nhật ký).
 */
public interface PaymentWebhookService {

    void handle(String rawBody, String clientIp);
}

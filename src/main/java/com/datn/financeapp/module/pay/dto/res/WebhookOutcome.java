package com.datn.financeapp.module.pay.dto.res;

import com.datn.financeapp.module.pay.enums.WebhookResult;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;

/**
 * Kết quả của một lần xử lý webhook, dùng để ghi vào dashboard. Không chứa dữ liệu cá nhân của người chuyển tiền.
 */
public record WebhookOutcome(
        WebhookResult result,
        Long orderCode,
        String paymentLinkId,
        Long amount,
        String payosCode,
        Boolean success,
        String message
) {

    public static WebhookOutcome of(WebhookResult result, Webhook webhook, WebhookData data, String message) {
        return new WebhookOutcome(
                result,
                data == null ? null : data.getOrderCode(),
                data == null ? null : data.getPaymentLinkId(),
                data == null ? null : data.getAmount(),
                webhook == null ? null : webhook.getCode(),
                webhook == null ? null : webhook.getSuccess(),
                message);
    }

    public static WebhookOutcome invalidBody(String message) {
        return new WebhookOutcome(WebhookResult.INVALID_BODY, null, null, null, null, null, message);
    }

    /** Dữ liệu lấy từ webhook CHƯA được xác thực (bên gửi tự khai). */
    public static WebhookOutcome invalidSignature(Webhook webhook) {
        return of(WebhookResult.INVALID_SIGNATURE, webhook, webhook == null ? null : webhook.getData(),
                "Sai chữ ký hoặc dữ liệu bị sửa");
    }

    public static WebhookOutcome error(Webhook webhook, Throwable ex) {
        String text = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        return of(WebhookResult.ERROR, webhook, webhook == null ? null : webhook.getData(), text);
    }
}

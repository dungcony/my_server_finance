package com.datn.financeapp.module.pay.controller;

import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.pay.dto.res.PaymentListRes;
import com.datn.financeapp.module.pay.dto.res.PaymentStatusRes;
import com.datn.financeapp.module.pay.enums.PaymentStatus;
import com.datn.financeapp.module.pay.enums.PaymentType;
import com.datn.financeapp.module.pay.exceptions.PaymentException;
import com.datn.financeapp.module.pay.service.PaymentService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.payos.model.webhooks.Webhook;

import java.util.UUID;

/**
 * Thanh toán payOS. Tạo đơn nằm ở module sở hữu hàng hoá (VD: {@code POST /users/me/premium-upgrades}),
 * controller này chỉ lo tra cứu trạng thái, lịch sử và các endpoint payOS gọi về.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Payment")
public class PaymentController {

    private static final String RETURN_PAGE = "<!doctype html><html lang=\"vi\"><head><meta charset=\"utf-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>Thanh toán</title></head>"
            + "<body><p>Bạn có thể đóng cửa sổ này và quay lại ứng dụng.</p></body></html>";

    // payOS gửi JSON camelCase, không đi qua naming strategy snake_case của Spring
    private static final ObjectMapper PAYOS_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final PaymentService paymentService;

    @Operation(summary = "Lịch sử thanh toán của tôi")
    @GetMapping("/payments")
    public ApiResponse<PaymentListRes> list(
            @RequestParam(name = "status", required = false) PaymentStatus status,
            @RequestParam(name = "type", required = false) PaymentType type,
            Pageable pageable) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(paymentService.listMine(userId, status, type, pageable));
    }

    @Operation(summary = "Trạng thái một đơn thanh toán của tôi", description = "Client polling endpoint này sau khi mở checkout_url")
    @GetMapping("/payments/{order_code}")
    public ApiResponse<PaymentStatusRes> status(@PathVariable("order_code") long orderCode) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(paymentService.getStatus(userId, orderCode));
    }

    @Operation(summary = "Huỷ một đơn thanh toán của tôi", description = "Huỷ đơn PENDING và link payOS tương ứng")
    @DeleteMapping("/payments/{order_code}")
    public ApiResponse<PaymentStatusRes> cancel(@PathVariable("order_code") long orderCode) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(paymentService.cancel(userId, orderCode));
    }

    @Operation(summary = "payOS webhook", description = "Endpoint nhận webhook thanh toán từ payOS")
    @PostMapping("/public/payments/payos/webhook")
    public ApiResponse<Void> payOsWebhook(@RequestBody String body) {
        paymentService.processWebhook(parseWebhook(body));
        return ApiResponse.of(null);
    }

    @Operation(summary = "payOS return URL", description = "Trang đích sau khi thanh toán, không đổi dữ liệu")
    @GetMapping(value = "/public/payments/payos/return", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public String payOsReturn() {
        return RETURN_PAGE;
    }

    @Operation(summary = "payOS cancel URL", description = "Trang đích khi người dùng huỷ thanh toán, không đổi dữ liệu")
    @GetMapping(value = "/public/payments/payos/cancel", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public String payOsCancel() {
        return RETURN_PAGE;
    }

    private Webhook parseWebhook(String body) {
        try {
            return PAYOS_MAPPER.readValue(body, Webhook.class);
        } catch (JsonProcessingException ex) {
            throw new PaymentException(ErrorCode.PAYMENT_WEBHOOK_INVALID);
        }
    }
}

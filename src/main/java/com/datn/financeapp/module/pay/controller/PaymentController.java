package com.datn.financeapp.module.pay.controller;

import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.security.ClientIpResolver;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.pay.dto.res.PaymentListRes;
import com.datn.financeapp.module.pay.dto.res.PaymentStatusRes;
import com.datn.financeapp.module.pay.enums.PaymentStatus;
import com.datn.financeapp.module.pay.enums.PaymentType;
import com.datn.financeapp.module.pay.service.PaymentService;
import com.datn.financeapp.module.pay.service.PaymentWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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

import java.util.UUID;

/**
 * Controller xử lý các yêu cầu tra cứu thanh toán từ phía người dùng và nhận callback từ payOS.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Payment")
public class PaymentController {

    // nội dung HTML đơn giản hiển thị khi người dùng được payOS điều hướng quay lại trình duyệt
    private static final String RETURN_PAGE = "<!doctype html><html lang=\"vi\"><head><meta charset=\"utf-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>Thanh toán</title></head>"
            + "<body><p>Bạn có thể đóng cửa sổ này và quay lại ứng dụng.</p></body></html>";

    private final PaymentService paymentService;
    private final PaymentWebhookService paymentWebhookService;

    // lấy lịch sử các đơn thanh toán của người dùng đang đăng nhập với bộ lọc và phân trang
    @Operation(summary = "Lịch sử thanh toán của tôi")
    @GetMapping("/payments")
    public ApiResponse<PaymentListRes> list(
            @RequestParam(name = "status", required = false) PaymentStatus status,
            @RequestParam(name = "type", required = false) PaymentType type,
            Pageable pageable) {
        // lấy mã định danh người dùng từ JWT context
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(paymentService.listMine(userId, status, type, pageable));
    }

    // kiểm tra trạng thái chi tiết của một đơn hàng, phục vụ cơ chế polling từ app
    @Operation(summary = "Trạng thái một đơn thanh toán của tôi", description = "Client polling endpoint này sau khi mở checkout_url")
    @GetMapping("/payments/{order_code}")
    public ApiResponse<PaymentStatusRes> status(@PathVariable("order_code") long orderCode) {
        // chỉ cho phép người dùng xem đơn của chính mình
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(paymentService.getStatus(userId, orderCode));
    }

    // người dùng chủ động huỷ đơn thanh toán PENDING
    @Operation(summary = "Huỷ một đơn thanh toán của tôi", description = "Huỷ đơn PENDING và link payOS tương ứng")
    @DeleteMapping("/payments/{order_code}")
    public ApiResponse<PaymentStatusRes> cancel(@PathVariable("order_code") long orderCode) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(paymentService.cancel(userId, orderCode));
    }

    // tiếp nhận webhook thông báo thanh toán thành công được gọi từ máy chủ payOS
    @Operation(summary = "payOS webhook", description = "Endpoint nhận webhook thanh toán từ payOS")
    @PostMapping("/public/payments/payos/webhook")
    public ApiResponse<Void> payOsWebhook(@RequestBody String body, HttpServletRequest request) {
        // ghi nhật ký và đối soát trước khi chuyển cho PaymentService xử lý
        paymentWebhookService.handle(body, ClientIpResolver.resolve(request));
        return ApiResponse.of(null);
    }

    // trang đích khi thanh toán thành công trên cổng payOS và quay lại hệ thống
    @Operation(summary = "payOS return URL", description = "Trang đích sau khi thanh toán, không đổi dữ liệu")
    @GetMapping(value = "/public/payments/payos/return", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public String payOsReturn() {
        return RETURN_PAGE;
    }

    // trang đích khi người dùng bấm huỷ thanh toán trên giao diện của payOS
    @Operation(summary = "payOS cancel URL", description = "Trang đích khi người dùng huỷ thanh toán, không đổi dữ liệu")
    @GetMapping(value = "/public/payments/payos/cancel", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public String payOsCancel() {
        return RETURN_PAGE;
    }
}

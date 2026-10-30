package com.datn.financeapp.module.pay.controller;

import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.module.pay.dto.res.PaymentAdminListRes;
import com.datn.financeapp.module.pay.dto.res.PaymentAdminRes;
import com.datn.financeapp.module.pay.dto.res.WebhookLogListRes;
import com.datn.financeapp.module.pay.dto.res.WebhookSummaryRes;
import com.datn.financeapp.module.pay.enums.PaymentStatus;
import com.datn.financeapp.module.pay.enums.PaymentType;
import com.datn.financeapp.module.pay.enums.WebhookResult;
import com.datn.financeapp.module.pay.service.PaymentAdminService;
import com.datn.financeapp.module.pay.service.PaymentWebhookLogAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Endpoint quản trị tra cứu thanh toán và đối soát nhật ký webhook cho quản trị viên hệ thống.
 */
@RestController
@RequestMapping("/payments/admin")
@RequiredArgsConstructor
public class PaymentAdminController {

    // biểu thức SpEL kiểm tra quyền xem dữ liệu thanh toán của quản trị viên
    private static final String PERM_READ = "hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).PAYMENT_READ.name()) or hasAnyAuthority('ROLE_ADMIN', 'ADMIN')";

    private final PaymentAdminService paymentAdminService;
    private final PaymentWebhookLogAdminService webhookLogAdminService;

    // danh sách phân trang tất cả đơn thanh toán với bộ lọc người dùng, trạng thái và thời gian
    @PreAuthorize(PERM_READ)
    @GetMapping
    public ApiResponse<PaymentAdminListRes> list(
            @RequestParam(name = "user_id", required = false) UUID userId,
            @RequestParam(name = "status", required = false) PaymentStatus status,
            @RequestParam(name = "type", required = false) PaymentType type,
            @RequestParam(name = "from_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(name = "to_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Pageable pageable) {
        return ApiResponse.of(paymentAdminService.list(userId, status, type, fromDate, toDate, pageable));
    }

    // tra cứu nhật ký các lần payOS bắn webhook về hệ thống để đối soát lỗi
    @PreAuthorize(PERM_READ)
    @GetMapping("/webhook-logs")
    public ApiResponse<WebhookLogListRes> webhookLogs(
            @RequestParam(name = "result", required = false) WebhookResult result,
            @RequestParam(name = "only_failed", defaultValue = "false") boolean onlyFailed,
            @RequestParam(name = "order_code", required = false) Long orderCode,
            @RequestParam(name = "from_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(name = "to_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Pageable pageable) {
        return ApiResponse.of(webhookLogAdminService.list(result, onlyFailed, orderCode, fromDate, toDate, pageable));
    }

    // tổng hợp thống kê tình trạng webhook và cảnh báo đơn thiếu webhook
    @PreAuthorize(PERM_READ)
    @GetMapping("/webhook-logs/summary")
    public ApiResponse<WebhookSummaryRes> webhookSummary(@RequestParam(name = "hours", defaultValue = "24") int hours) {
        return ApiResponse.of(webhookLogAdminService.summary(hours));
    }

    // xem chi tiết một đơn thanh toán bất kỳ kèm định danh người nạp và link payOS
    @PreAuthorize(PERM_READ)
    @GetMapping("/{order_code}")
    public ApiResponse<PaymentAdminRes> detail(@PathVariable("order_code") long orderCode) {
        return ApiResponse.of(paymentAdminService.detail(orderCode));
    }
}

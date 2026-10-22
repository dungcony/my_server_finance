package com.datn.financeapp.module.pay.controller;

import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.module.pay.dto.res.PaymentAdminListRes;
import com.datn.financeapp.module.pay.dto.res.PaymentAdminRes;
import com.datn.financeapp.module.pay.enums.PaymentStatus;
import com.datn.financeapp.module.pay.enums.PaymentType;
import com.datn.financeapp.module.pay.service.PaymentAdminService;
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
 * Tra cứu thanh toán toàn hệ thống cho quản trị viên (chỉ đọc).
 *
 * <ul>
 *   <li>{@code GET /payments/admin}: danh sách phân trang theo bộ lọc</li>
 *   <li>{@code GET /payments/admin/{order_code}}: chi tiết một đơn bất kỳ</li>
 * </ul>
 */
@RestController
@RequestMapping("/payments/admin")
@RequiredArgsConstructor
public class PaymentAdminController {

    private static final String PERM_READ = "hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).PAYMENT_READ.name()) or hasAnyAuthority('ROLE_ADMIN', 'ADMIN')";

    private final PaymentAdminService paymentAdminService;

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

    @PreAuthorize(PERM_READ)
    @GetMapping("/{order_code}")
    public ApiResponse<PaymentAdminRes> detail(@PathVariable("order_code") long orderCode) {
        return ApiResponse.of(paymentAdminService.detail(orderCode));
    }
}

package com.datn.financeapp.module.debt.controller;

import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.debt.dto.request.CreateDebtRequest;
import com.datn.financeapp.module.debt.dto.request.CreatePaymentRequest;
import com.datn.financeapp.module.debt.dto.request.UpdateDebtRequest;
import com.datn.financeapp.module.debt.dto.request.WriteOffRequest;
import com.datn.financeapp.module.debt.dto.response.CreateDebtResponse;
import com.datn.financeapp.module.debt.dto.response.CreatePaymentResponse;
import com.datn.financeapp.module.debt.dto.response.DebtDetailResponse;
import com.datn.financeapp.module.debt.dto.response.DebtListItemResponse;
import com.datn.financeapp.module.debt.dto.response.DebtSummaryResponse;
import com.datn.financeapp.module.debt.service.DebtAdminService;
import com.datn.financeapp.module.debt.service.DebtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller xử lý các yêu cầu HTTP quản lý sổ nợ, đợt thanh toán và xóa nợ.
 * Bao gồm cả luồng người dùng thông thường và luồng dành cho Quản trị viên (Admin).
 */
@RestController
@RequestMapping("/debts")
@RequiredArgsConstructor
public class DebtController {

    private final DebtService debtService;
    private final DebtAdminService debtAdminService;

    // =========================================================================
    // Luồng người dùng thông thường (User Flow)
    // =========================================================================

    @GetMapping
    public ApiResponse<List<DebtListItemResponse>> list(
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "status", required = false, defaultValue = "outstanding") String status,
            @RequestParam(name = "is_overdue", required = false) Boolean isOverdue) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(debtService.list(userId, type, status, isOverdue));
    }

    @GetMapping("/summary")
    public ApiResponse<DebtSummaryResponse> summary() {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(debtService.summary(userId));
    }

    @GetMapping("/{id}")
    public ApiResponse<DebtDetailResponse> detail(@PathVariable UUID id) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(debtService.detail(userId, id));
    }

    @PostMapping
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateDebtResponse> create(@Valid @RequestBody CreateDebtRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(debtService.create(userId, req));
    }

    @PatchMapping("/{id}")
    public ApiResponse<DebtListItemResponse> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateDebtRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(debtService.update(userId, id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        UUID userId = SecurityContextUtil.currentUserId();
        debtService.delete(userId, id);
        return ApiResponse.of(null);
    }

    @PostMapping("/{id}/payments")
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreatePaymentResponse> addPayment(
            @PathVariable UUID id, @Valid @RequestBody CreatePaymentRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(debtService.addPayment(userId, id, req));
    }

    @DeleteMapping("/{id}/payments/{paymentId}")
    public ApiResponse<Void> cancelPayment(@PathVariable UUID id, @PathVariable UUID paymentId) {
        UUID userId = SecurityContextUtil.currentUserId();
        debtService.cancelPayment(userId, id, paymentId);
        return ApiResponse.of(null);
    }

    @PostMapping("/{id}/write-off")
    public ApiResponse<DebtListItemResponse> writeOff(
            @PathVariable UUID id, @RequestBody(required = false) WriteOffRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(debtService.writeOff(userId, id, req));
    }

    // =========================================================================
    // Luồng Quản trị viên (Admin Flow - DebtAdminService)
    // =========================================================================

    @PreAuthorize("hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).DEBT_READ.getValue())")
    @GetMapping("/admin")
    public ApiResponse<List<DebtListItemResponse>> adminList(
            @RequestParam(name = "user_id", required = false) UUID userId,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "is_overdue", required = false) Boolean isOverdue) {
        return ApiResponse.of(debtAdminService.list(userId, type, status, isOverdue));
    }

    @PreAuthorize("hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).DEBT_READ.getValue())")
    @GetMapping("/admin/summary")
    public ApiResponse<DebtSummaryResponse> adminSummary(
            @RequestParam(name = "user_id", required = false) UUID userId) {
        return ApiResponse.of(debtAdminService.summary(userId));
    }

    @PreAuthorize("hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).DEBT_READ.getValue())")
    @GetMapping("/admin/{id}")
    public ApiResponse<DebtDetailResponse> adminDetail(@PathVariable UUID id) {
        return ApiResponse.of(debtAdminService.detail(id));
    }

    @PreAuthorize("hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).DEBT_UPDATE.getValue())")
    @PostMapping("/admin")
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateDebtResponse> adminCreate(
            @RequestParam(name = "user_id") UUID userId,
            @Valid @RequestBody CreateDebtRequest req) {
        return ApiResponse.of(debtAdminService.createForUser(userId, req));
    }

    @PreAuthorize("hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).DEBT_UPDATE.getValue())")
    @PatchMapping("/admin/{id}")
    public ApiResponse<DebtListItemResponse> adminUpdate(
            @PathVariable UUID id, @Valid @RequestBody UpdateDebtRequest req) {
        return ApiResponse.of(debtAdminService.update(id, req));
    }

    @PreAuthorize("hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).DEBT_UPDATE.getValue())")
    @PostMapping("/admin/{id}/payments")
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreatePaymentResponse> adminAddPayment(
            @PathVariable UUID id, @Valid @RequestBody CreatePaymentRequest req) {
        return ApiResponse.of(debtAdminService.addPaymentForUser(id, req));
    }

    @PreAuthorize("hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).DEBT_UPDATE.getValue())")
    @PostMapping("/admin/{id}/write-off")
    public ApiResponse<DebtListItemResponse> adminWriteOff(
            @PathVariable UUID id, @RequestBody(required = false) WriteOffRequest req) {
        return ApiResponse.of(debtAdminService.writeOff(id, req));
    }

    @PreAuthorize("hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).DEBT_DELETE.getValue())")
    @DeleteMapping("/admin/{id}/payments/{paymentId}")
    public ApiResponse<Void> adminCancelPayment(@PathVariable UUID id, @PathVariable UUID paymentId) {
        debtAdminService.cancelPayment(id, paymentId);
        return ApiResponse.of(null);
    }

    @PreAuthorize("hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).DEBT_DELETE.getValue())")
    @DeleteMapping("/admin/{id}")
    public ApiResponse<Void> adminDelete(@PathVariable UUID id) {
        debtAdminService.delete(id);
        return ApiResponse.of(null);
    }
}

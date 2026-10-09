package com.datn.financeapp.module.debt.controller;

import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.debt.dto.request.CreateDebtRequest;
import com.datn.financeapp.module.debt.dto.response.CreateDebtResponse;
import com.datn.financeapp.module.debt.dto.request.CreatePaymentRequest;
import com.datn.financeapp.module.debt.dto.response.CreatePaymentResponse;
import com.datn.financeapp.module.debt.dto.response.DebtDetailResponse;
import com.datn.financeapp.module.debt.dto.response.DebtListItemResponse;
import com.datn.financeapp.module.debt.dto.response.DebtSummaryResponse;
import com.datn.financeapp.module.debt.dto.request.UpdateDebtRequest;
import com.datn.financeapp.module.debt.dto.request.WriteOffRequest;
import com.datn.financeapp.module.debt.service.DebtService;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller xử lý các yêu cầu HTTP quản lý sổ nợ, đợt thanh toán và xóa nợ.
 * <p>
 * <b>Các endpoint chính:</b>
 * <ul>
 *   <li>{@link #list(String, String, Boolean)}: {@code GET /debts} - Danh sách khoản nợ</li>
 *   <li>{@link #summary()}: {@code GET /debts/summary} - Báo cáo tổng quan sổ nợ</li>
 *   <li>{@link #detail(UUID)}: {@code GET /debts/{id}} - Chi tiết khoản nợ</li>
 *   <li>{@link #create(CreateDebtRequest)}: {@code POST /debts} - Tạo mới khoản nợ/cho vay</li>
 *   <li>{@link #update(UUID, UpdateDebtRequest)}: {@code PATCH /debts/{id}} - Cập nhật khoản nợ</li>
 *   <li>{@link #delete(UUID)}: {@code DELETE /debts/{id}} - Xóa khoản nợ</li>
 *   <li>{@link #addPayment(UUID, CreatePaymentRequest)}: {@code POST /debts/{id}/payments} - Ghi nhận thanh toán</li>
 *   <li>{@link #cancelPayment(UUID, UUID)}: {@code DELETE /debts/{id}/payments/{paymentId}} - Hủy thanh toán</li>
 *   <li>{@link #writeOff(UUID, WriteOffRequest)}: {@code POST /debts/{id}/write-off} - Xóa nợ xấu</li>
 * </ul>
 * </p>
 *
 * @see DebtService
 */
@RestController
@RequestMapping("/debts")
@RequiredArgsConstructor
public class DebtController {

    private final DebtService debtService;

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
}

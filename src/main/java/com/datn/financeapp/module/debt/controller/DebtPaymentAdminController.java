package com.datn.financeapp.module.debt.controller;

import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.module.debt.dto.request.DebtPaymentCreateReq;
import com.datn.financeapp.module.debt.dto.request.DebtPaymentFilterReq;
import com.datn.financeapp.module.debt.dto.request.DebtPaymentUpdateReq;
import com.datn.financeapp.module.debt.dto.response.DebtPaymentListRes;
import com.datn.financeapp.module.debt.dto.response.DebtPaymentRes;
import com.datn.financeapp.module.debt.service.DebtPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin/debts/{debtId}/payments")
@RequiredArgsConstructor
public class DebtPaymentAdminController {

    private final DebtPaymentService debtPaymentService;

    private static final String MANAGE_DEBT_READ =
            "hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).DEBT_READ.name())";
    private static final String MANAGE_DEBT_UPDATE =
            "hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).DEBT_UPDATE.name())";
    private static final String MANAGE_DEBT_DELETE =
            "hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).DEBT_DELETE.name())";

    @PreAuthorize(MANAGE_DEBT_READ)
    @GetMapping
    public ApiResponse<DebtPaymentListRes> adminGets(
            @PathVariable UUID debtId,
            DebtPaymentFilterReq filter,
            Pageable pageable) {
        return ApiResponse.of(debtPaymentService.gets(debtId, filter, pageable));
    }

    @PreAuthorize(MANAGE_DEBT_UPDATE)
    @PostMapping
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DebtPaymentRes> adminCreate(
            @PathVariable UUID debtId,
            @Valid @RequestBody DebtPaymentCreateReq req) {
        return ApiResponse.of(debtPaymentService.create(debtId, req, true));
    }

    @PreAuthorize(MANAGE_DEBT_UPDATE)
    @PatchMapping("/{paymentId}")
    public ApiResponse<DebtPaymentRes> adminUpdate(
            @PathVariable UUID debtId,
            @PathVariable UUID paymentId,
            @Valid @RequestBody DebtPaymentUpdateReq req) {
        return ApiResponse.of(debtPaymentService.update(paymentId, req, true));
    }

    @PreAuthorize(MANAGE_DEBT_DELETE)
    @DeleteMapping("/{paymentId}")
    public ApiResponse<Void> adminDelete(
            @PathVariable UUID debtId,
            @PathVariable UUID paymentId) {
        debtPaymentService.delete(paymentId, true);
        return ApiResponse.of(null);
    }
}

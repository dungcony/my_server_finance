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
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/debts/{debtId}/payments")
@RequiredArgsConstructor
public class DebtPaymentController {

    private final DebtPaymentService debtPaymentService;

    @GetMapping
    public ApiResponse<DebtPaymentListRes> gets(
            @PathVariable UUID debtId,
            DebtPaymentFilterReq filter,
            Pageable pageable) {
        return ApiResponse.of(debtPaymentService.gets(debtId, filter, pageable));
    }

    @PostMapping
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DebtPaymentRes> create(
            @PathVariable UUID debtId,
            @Valid @RequestBody DebtPaymentCreateReq req) {
        return ApiResponse.of(debtPaymentService.create(debtId, req, false));
    }

    @PatchMapping("/{paymentId}")
    public ApiResponse<DebtPaymentRes> update(
            @PathVariable UUID debtId,
            @PathVariable UUID paymentId,
            @Valid @RequestBody DebtPaymentUpdateReq req) {
        return ApiResponse.of(debtPaymentService.update(paymentId, req, false));
    }

    @DeleteMapping("/{paymentId}")
    public ApiResponse<Void> delete(
            @PathVariable UUID debtId,
            @PathVariable UUID paymentId) {
        debtPaymentService.delete(paymentId, false);
        return ApiResponse.of(null);
    }
}

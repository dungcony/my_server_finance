package com.datn.financeapp.module.debt.controller;

import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.module.debt.dto.request.DebtCreateReq;
import com.datn.financeapp.module.debt.dto.request.DebtFilterReq;
import com.datn.financeapp.module.debt.dto.request.DebtUpdateReq;
import com.datn.financeapp.module.debt.service.DebtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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


    @GetMapping("/{id}")
    public ApiResponse<?> detail(@PathVariable UUID id) {
        return ApiResponse.of(debtService.get(id));
    }

    @PostMapping
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<?> create(@Valid @RequestBody DebtCreateReq req) {
        return ApiResponse.of(debtService.create(req));
    }

    @GetMapping
    public ApiResponse<?> gets(
            DebtFilterReq filter,
            Pageable pageable) {
        return ApiResponse.of(debtService.gets(filter, pageable));
    }

    @PatchMapping("/{id}")
    public ApiResponse<?> update(
            @PathVariable UUID id, @Valid @RequestBody DebtUpdateReq req) {
        return ApiResponse.of(debtService.update(id, req, false));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        debtService.delete(id, false);
        return ApiResponse.of(null);
    }

}

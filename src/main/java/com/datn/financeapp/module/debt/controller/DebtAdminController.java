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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin/debts")
@RequiredArgsConstructor
public class DebtAdminController {

    private final DebtService debtService;

    private static final String MANAGE_DEBT_READ =
            "hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).DEBT_READ.name())";
    private static final String MANAGE_DEBT_UPDATE =
            "hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).DEBT_UPDATE.name())";
    private static final String MANAGE_DEBT_DELETE =
            "hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).DEBT_DELETE.name())";

    @PreAuthorize(MANAGE_DEBT_READ)
    @GetMapping("/user/{user_id}")
    public ApiResponse<?> list(
            @PathVariable("user_id") UUID userId,
            DebtFilterReq filter,
            Pageable pageable
    ) {
        return ApiResponse.of(debtService.gets(userId, filter, pageable));
    }

    @PreAuthorize(MANAGE_DEBT_READ)
    @GetMapping("/{id}")
    public ApiResponse<?> get(@PathVariable UUID id) {
        return ApiResponse.of(debtService.get(id));
    }

    @PreAuthorize(MANAGE_DEBT_UPDATE)
    @PostMapping
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<?> adminCreate(
            @RequestParam(name = "user_id") UUID userId,
            @Valid @RequestBody DebtCreateReq req) {
        return ApiResponse.of(debtService.create(userId, req));
    }

    @PreAuthorize(MANAGE_DEBT_UPDATE)
    @PatchMapping("/{id}")
    public ApiResponse<?> adminUpdate(
            @PathVariable UUID id,
            @Valid @RequestBody DebtUpdateReq req) {
        return ApiResponse.of(debtService.update(id, req, true));
    }

    @PreAuthorize(MANAGE_DEBT_DELETE)
    @DeleteMapping("/{id}")
    public ApiResponse<Void> adminDelete(@PathVariable UUID id) {
        debtService.delete(id, true);
        return ApiResponse.of(null);
    }
}

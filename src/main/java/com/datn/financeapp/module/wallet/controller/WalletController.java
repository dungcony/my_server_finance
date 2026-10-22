package com.datn.financeapp.module.wallet.controller;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.module.wallet.dto.request.WalletCreateReq;
import com.datn.financeapp.module.wallet.dto.request.WalletFillterReq;
import com.datn.financeapp.module.wallet.dto.request.WalletupdateReq;
import com.datn.financeapp.module.wallet.enums.WalletType;
import com.datn.financeapp.module.wallet.service.WalletBehavierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST Controller quản lý các thao tác liên quan đến ví tài chính.
 */
@RestController
@RequestMapping("/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletBehavierService walletBehavierService;

    private static final String MANAGE_GET = "hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).WALLET_READ.name())";
    private static final String MANAGE_UPDATE = "hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).WALLET_UPDATE.name())";
    private static final String MANAGE_DELETE = "hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).WALLET_DELETE.name())";

    @GetMapping
    public ApiResponse<?> gets(
            @ModelAttribute WalletFillterReq filter,
            Pageable pageable
    ) {
        return ApiResponse.of(walletBehavierService.gets(filter, pageable, false));
    }

    @GetMapping("/{id}")
    public ApiResponse<?> get(@PathVariable UUID id) {
        return ApiResponse.of(walletBehavierService.get(id, false));
    }

    @PostMapping
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<?> create(@Valid @RequestBody WalletCreateReq req) {
        if (req.type() == WalletType.SAVINGS) {
            throw new BusinessException(
                    ErrorCode.WALLET_SAVINGS_MANUAL_CREATE_NOT_ALLOWED,
                    "Ví tiết kiệm chỉ được tạo tự động thông qua Mục tiêu tiết kiệm."
            );
        }
        return ApiResponse.of(walletBehavierService.create(req, false));
    }

    @PatchMapping("/{id}")
    public ApiResponse<?> update(
            @PathVariable UUID id,
            @Valid @RequestBody WalletupdateReq req) {
        return ApiResponse.of(walletBehavierService.update(id, req, false));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> softDelete(
            @PathVariable UUID id
    ) {
        return ApiResponse.of(walletBehavierService.softDelete(id, false));
    }

    @PreAuthorize(MANAGE_GET)
    @GetMapping("/admin/{user_id}")
    public ApiResponse<?> adGets(
            @PathVariable("user_id") UUID userId,
            WalletFillterReq filter,
            Pageable pageable) {
        WalletFillterReq adminFilter = filter != null ? filter.withUser(userId)
                : new WalletFillterReq(null, null, null, null, null, null, null, false, userId);
        return ApiResponse.of(walletBehavierService.gets(adminFilter, pageable, true));
    }

    @PreAuthorize(MANAGE_GET)
    @GetMapping("/admin/all")
    public ApiResponse<?> getAll(
            WalletFillterReq filter,
            Pageable pageable) {
        return ApiResponse.of(walletBehavierService.gets(filter, pageable, true));
    }

    @PreAuthorize(MANAGE_GET)
    @GetMapping("/admin/detail/{id}")
    public ApiResponse<?> adminGet(@PathVariable UUID id) {
        return ApiResponse.of(walletBehavierService.get(id, true));
    }

    @PreAuthorize(MANAGE_UPDATE)
    @PostMapping("/admin/{user_id}")
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<?> adminCreate(
            @PathVariable("user_id") UUID userId,
            @Valid @RequestBody WalletCreateReq req) {
        WalletCreateReq adminReq = req.userId() == null ?
                new WalletCreateReq(req.name(), req.type(), req.balance(), userId, req.iconCode(), req.color()) : req;
        return ApiResponse.of(walletBehavierService.create(adminReq, true));
    }

    @PreAuthorize(MANAGE_UPDATE)
    @PatchMapping("/admin/{id}")
    public ApiResponse<?> adUpdate(
            @PathVariable UUID id,
            @Valid @RequestBody WalletupdateReq req
    ) {
        return ApiResponse.of(walletBehavierService.update(id, req, true));
    }

    @PreAuthorize(MANAGE_DELETE)
    @DeleteMapping("/admin/{id}")
    public ApiResponse<Void> adminDelete(
            @PathVariable UUID id
    ) {
        return ApiResponse.of(walletBehavierService.softDelete(id, true));
    }

}

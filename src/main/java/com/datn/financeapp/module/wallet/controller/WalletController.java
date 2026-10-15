package com.datn.financeapp.module.wallet.controller;

import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.module.wallet.dto.request.WalletCreateReq;
import com.datn.financeapp.module.wallet.dto.request.WalletupdateReq;
import com.datn.financeapp.module.wallet.service.WalletBehavierService;
import jakarta.validation.Valid;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * REST Controller quản lý các thao tác liên quan đến ví tài chính.
 */
@RestController
@RequestMapping("/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletBehavierService walletBehavierService;

    private static final String MANAGE_GET = "hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).WALLET_READ.getValue())";
    private static final String MANAGE_UPDATE = "hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).WALLET_UPDATE.getValue())";
    private static final String MANAGE_DELETE = "hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).WALLET_DELETE.getValue())";

    @GetMapping
    public ApiResponse<?> gets(
            Pageable pageable
    ) {
        return ApiResponse.of(walletBehavierService.gets(null, pageable));
    }

    @PostMapping
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<?> create(@Valid @RequestBody WalletCreateReq req) {
        return ApiResponse.of(walletBehavierService.create(null, req));
    }

    @PatchMapping("/{id}")
    public ApiResponse<?> update(
            @PathVariable UUID id,
            @Valid @RequestBody WalletupdateReq req) {
        return ApiResponse.of(walletBehavierService.update(null, id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> softDelete(
            @PathVariable UUID id
    ) {
        return ApiResponse.of(walletBehavierService.softDelete(null, id));
    }

    @PreAuthorize(MANAGE_GET)
    @GetMapping("/admin/{user_id}")
    public ApiResponse<?> adGets(
            @PathVariable("user_id") UUID userId,
            Pageable pageable) {
        return ApiResponse.of(walletBehavierService.gets(userId, pageable));
    }

    @PreAuthorize(MANAGE_GET)
    @GetMapping("/admin/all")
    public ApiResponse<?> getAll(Pageable pageable) {
        return ApiResponse.of(walletBehavierService.getAll(pageable));
    }

    @PreAuthorize(MANAGE_UPDATE)
    @PostMapping("/admin/{user_id}")
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<?> admiCreate(
            @Valid @RequestBody WalletCreateReq req,
            @PathVariable UUID user_id) {
        return ApiResponse.of(walletBehavierService.create(user_id, req));
    }

    @PreAuthorize(MANAGE_UPDATE)
    @PatchMapping("/admin/{id}")
    public ApiResponse<?> adUpdate(
            @PathVariable UUID id,
            @Valid @RequestBody WalletupdateReq req,
            @RequestParam(name = "user_id", required = false) UUID userId
    ) {
        return ApiResponse.of(walletBehavierService.update(userId, id, req));
    }

    @PreAuthorize(MANAGE_DELETE)
    @DeleteMapping("/admin/{id}")
    public ApiResponse<Void> adminDelete(
            @PathVariable UUID id,
            @RequestParam(name = "user_id", required = false) UUID userId
    ) {
        return ApiResponse.of(walletBehavierService.softDelete(userId, id));
    }

}

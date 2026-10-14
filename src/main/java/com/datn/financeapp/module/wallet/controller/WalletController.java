package com.datn.financeapp.module.wallet.controller;

import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.wallet.dto.request.WalletCreateReq;
import com.datn.financeapp.module.wallet.dto.request.WalletupdateReq;
import com.datn.financeapp.module.wallet.dto.response.ReconcileResponse;
import com.datn.financeapp.module.wallet.dto.response.WalletRes;
import com.datn.financeapp.module.wallet.service.WalletBehavierService;
import com.datn.financeapp.module.wallet.service.WalletService;
import com.datn.financeapp.module.wallet.service.WalletTransferService;
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
 * REST Controller quản lý các thao tác liên quan đến ví tài chính.
 */
@RestController
@RequestMapping("/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletBehavierService walletBehavierService;

    @GetMapping
    public ApiResponse<List<WalletRes>> gets() {
        return ApiResponse.of(walletBehavierService.gets());
    }

    @PostMapping
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<WalletRes> create(@Valid @RequestBody WalletCreateReq req) {
        return ApiResponse.of(walletBehavierService.create(req));
    }

    @PatchMapping("/{id}")
    public ApiResponse<WalletRes> update(
            @PathVariable UUID id,
            @Valid @RequestBody WalletupdateReq req) {
        return ApiResponse.of(walletBehavierService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> softDelete(@PathVariable UUID id) {
        return ApiResponse.of(walletBehavierService.softDelete(id));
    }
}

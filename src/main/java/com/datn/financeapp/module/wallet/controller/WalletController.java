package com.datn.financeapp.module.wallet.controller;

import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.wallet.dto.request.AdjustBalanceRequest;
import com.datn.financeapp.module.wallet.dto.response.AdjustBalanceResponse;
import com.datn.financeapp.module.wallet.dto.request.CreateWalletRequest;
import com.datn.financeapp.module.wallet.dto.response.ReconcileResponse;
import com.datn.financeapp.module.wallet.dto.request.ReorderWalletsRequest;
import com.datn.financeapp.module.wallet.dto.request.TransferRequest;
import com.datn.financeapp.module.wallet.dto.response.TransferResponse;
import com.datn.financeapp.module.wallet.dto.request.UpdateWalletRequest;
import com.datn.financeapp.module.wallet.dto.response.WalletDetailResponse;
import com.datn.financeapp.module.wallet.dto.response.WalletResponse;
import com.datn.financeapp.module.wallet.dto.response.WalletSummaryResponse;
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
 * Cung cấp API quản lý danh sách ví {@link #list}, tổng quan ví {@link #summary}, chi tiết ví {@link #detail},
 * tạo mới ví {@link #create}, cập nhật ví {@link #update}, xóa ví {@link #delete}, sắp xếp ví {@link #reorder},
 * chuyển tiền giữa các ví {@link #transfer}, điều chỉnh số dư {@link #adjustBalance} và đối chiếu số dư {@link #reconcile}.
 */
@RestController
@RequestMapping("/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final WalletTransferService walletTransferService;

    @GetMapping
    public ApiResponse<List<WalletResponse>> list(
            @RequestParam(required = false) String type,
            @RequestParam(name = "only_in_total", required = false) Boolean onlyInTotal,
            @RequestParam(name = "include_shared", required = false, defaultValue = "true") boolean includeShared) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(walletService.list(userId, type, onlyInTotal, includeShared));
    }

    @GetMapping("/summary")
    public ApiResponse<WalletSummaryResponse> summary() {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(walletService.summary(userId));
    }

    @GetMapping("/{id}")
    public ApiResponse<WalletDetailResponse> detail(@PathVariable UUID id) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(walletService.detail(userId, id));
    }

    @PostMapping
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<WalletResponse> create(@Valid @RequestBody CreateWalletRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(walletService.create(userId, req));
    }

    @PatchMapping("/{id}")
    public ApiResponse<WalletResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateWalletRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(walletService.update(userId, id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable UUID id,
            @RequestParam(name = "delete_transactions", required = false, defaultValue = "false")
            boolean deleteTransactions) {
        UUID userId = SecurityContextUtil.currentUserId();
        walletService.delete(userId, id, deleteTransactions);
        return ApiResponse.of(null);
    }

    @PatchMapping("/reorder")
    public ApiResponse<Void> reorder(@Valid @RequestBody ReorderWalletsRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        walletService.reorder(userId, req);
        return ApiResponse.of(null);
    }

    @PostMapping("/transfer")
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TransferResponse> transfer(@Valid @RequestBody TransferRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(walletTransferService.transfer(userId, req));
    }

    @PostMapping("/{id}/adjust-balance")
    @Idempotent
    public ApiResponse<AdjustBalanceResponse> adjustBalance(
            @PathVariable UUID id, @Valid @RequestBody AdjustBalanceRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(walletTransferService.adjustBalance(userId, id, req));
    }

    @PostMapping("/{id}/reconcile")
    public ApiResponse<ReconcileResponse> reconcile(
            @PathVariable UUID id,
            @RequestParam(name = "auto_fix", required = false, defaultValue = "false") boolean autoFix) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(walletTransferService.reconcile(userId, id, autoFix));
    }
}

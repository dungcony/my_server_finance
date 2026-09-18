package com.datn.financeapp.group.controller;

import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.group.dto.wallet.GroupWalletCreateReq;
import com.datn.financeapp.group.dto.wallet.GroupWalletReconcileReq;
import com.datn.financeapp.group.dto.wallet.GroupWalletReconcileRes;
import com.datn.financeapp.group.dto.wallet.GroupWalletRes;
import com.datn.financeapp.group.dto.wallet.GroupWalletUpdateReq;
import com.datn.financeapp.group.service.GroupWalletService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/groups/{groupId}")
@RequiredArgsConstructor
public class GroupWalletController {

    private final GroupWalletService groupWalletService;

    @GetMapping("/fund")
    public ApiResponse<GroupWalletRes> getFund(@PathVariable UUID groupId) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(groupWalletService.getFund(userId, groupId));
    }

    @PatchMapping("/fund")
    public ApiResponse<GroupWalletRes> updateFund(
            @PathVariable UUID groupId,
            @Valid @RequestBody GroupWalletUpdateReq req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(groupWalletService.updateFund(userId, groupId, req));
    }

    @PostMapping("/fund/reconcile")
    public ApiResponse<GroupWalletReconcileRes> reconcileFund(
            @PathVariable UUID groupId,
            @Valid @RequestBody GroupWalletReconcileReq req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(groupWalletService.reconcileFund(userId, groupId, req));
    }

    @PostMapping("/wallets")
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GroupWalletRes> create(
            @PathVariable UUID groupId,
            @Valid @RequestBody GroupWalletCreateReq req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(groupWalletService.create(userId, groupId, req));
    }

    @GetMapping("/wallets")
    public ApiResponse<List<GroupWalletRes>> list(@PathVariable UUID groupId) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(groupWalletService.list(userId, groupId));
    }

    @GetMapping("/wallets/{walletId}")
    public ApiResponse<GroupWalletRes> detail(
            @PathVariable UUID groupId,
            @PathVariable UUID walletId) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(groupWalletService.detail(userId, groupId, walletId));
    }

    @PatchMapping("/wallets/{walletId}")
    public ApiResponse<GroupWalletRes> update(
            @PathVariable UUID groupId,
            @PathVariable UUID walletId,
            @Valid @RequestBody GroupWalletUpdateReq req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(groupWalletService.update(userId, groupId, walletId, req));
    }

    @PostMapping("/wallets/{walletId}/reconcile")
    public ApiResponse<GroupWalletReconcileRes> reconcile(
            @PathVariable UUID groupId,
            @PathVariable UUID walletId,
            @Valid @RequestBody GroupWalletReconcileReq req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(groupWalletService.reconcile(userId, groupId, walletId, req));
    }
}

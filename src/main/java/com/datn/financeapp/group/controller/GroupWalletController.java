package com.datn.financeapp.group.controller;

import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.group.dto.request.wallet.GroupWalletReconcileReq;
import com.datn.financeapp.group.dto.response.wallet.GroupWalletReconcileRes;
import com.datn.financeapp.group.dto.response.wallet.GroupWalletRes;
import com.datn.financeapp.group.dto.request.wallet.GroupWalletUpdateReq;
import com.datn.financeapp.group.service.GroupWalletService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/groups/{groupId}")
@RequiredArgsConstructor
public class GroupWalletController {

    private final GroupWalletService groupWalletService;

    @GetMapping("/fund")
    public ApiResponse<GroupWalletRes> getFund(@PathVariable UUID groupId) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(groupWalletService.getWallet(userId, groupId));
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
}

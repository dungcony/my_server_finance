package com.datn.financeapp.group.controller;

import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.group.dto.request.fund.GroupFundReconcileReq;
import com.datn.financeapp.group.dto.request.fund.GroupFundUpdateReq;
import com.datn.financeapp.group.dto.response.fund.GroupFundReconcileRes;
import com.datn.financeapp.group.dto.response.fund.GroupFundRes;
import com.datn.financeapp.group.service.FundService;
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
public class GroupFundController {

    private final FundService fundService;

    @GetMapping("/fund")
    public ApiResponse<GroupFundRes> getFund(@PathVariable UUID groupId) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(fundService.getFund(userId, groupId));
    }

    @PatchMapping("/fund")
    public ApiResponse<GroupFundRes> updateFund(
            @PathVariable UUID groupId,
            @Valid @RequestBody GroupFundUpdateReq req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(fundService.updateFund(userId, groupId, req));
    }

    @PostMapping("/fund/reconcile")
    public ApiResponse<GroupFundReconcileRes> reconcileFund(
            @PathVariable UUID groupId,
            @Valid @RequestBody GroupFundReconcileReq req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(fundService.reconcileFund(userId, groupId, req));
    }
}

package com.datn.financeapp.module.group.controller;

import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.group.dto.request.fund.FundReconcileReq;
import com.datn.financeapp.module.group.dto.request.fund.FundKepperUpdateReq;
import com.datn.financeapp.module.group.dto.response.fund.GroupFundReconcileRes;
import com.datn.financeapp.module.group.dto.response.fund.FundRes;
import com.datn.financeapp.module.group.service.FundService;
import jakarta.validation.Valid;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/groups/{groupId}")
@RequiredArgsConstructor
public class FundController {

    private final FundService fundService;

    @PutMapping("/fund-kepper")
    public ApiResponse<FundRes> updateFund(
            @PathVariable UUID groupId,
            @Valid @RequestBody FundKepperUpdateReq req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(fundService.updateFundKeepper(groupId, req));
    }

    @PostMapping("/fund/reconcile")
    public ApiResponse<GroupFundReconcileRes> reconcileFund(
            @PathVariable UUID groupId,
            @Valid @RequestBody FundReconcileReq req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(fundService.reconcileFund(groupId, req));
    }
}

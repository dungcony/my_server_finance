package com.datn.financeapp.module.wallet.controller;

import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.module.wallet.service.ReconcilHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST Controller quản lý lịch sử đối chiếu số dư ví.
 */
@RestController
@RequestMapping("/wallets")
@RequiredArgsConstructor
public class ReconcilHistoryController {

    private final ReconcilHistoryService reconcilHistoryService;

    private static final String MANAGE_GET = "hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).WALLET_READ.getValue())";

    @GetMapping("/{id}/reconciliations")
    public ApiResponse<?> reconciliations(
            @PathVariable UUID id,
            Pageable pageable
    ) {
        return ApiResponse.of(reconcilHistoryService.getsByWallet(null, id, pageable));
    }

    @PreAuthorize(MANAGE_GET)
    @GetMapping("/admin/reconciliations")
    public ApiResponse<?> adminReconciliations(Pageable pageable) {
        return ApiResponse.of(reconcilHistoryService.gets(pageable));
    }
}

package com.datn.financeapp.module.transaction.controller;

import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.module.transaction.dto.request.TxnCreateReq;
import com.datn.financeapp.module.transaction.dto.request.TxnFilterReq;
import com.datn.financeapp.module.transaction.dto.request.TxnUpdateReq;
import com.datn.financeapp.module.transaction.service.TxnBehavierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * REST Controller quản lý các thao tác truy vấn, tạo mới, cập nhật và xóa giao dịch cá nhân.
 */
@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TxnController {

    private final TxnBehavierService txnBehavierService;

    // luồng người dùng thông thường

    @GetMapping
    public ApiResponse<?> gets(
            @ModelAttribute TxnFilterReq filters,
            Pageable page) {
        return ApiResponse.of(txnBehavierService.gets(filters, page, false));
    }

    @GetMapping("/{id}")
    public ApiResponse<?> detail(@PathVariable UUID id) {
        return ApiResponse.of(txnBehavierService.get(id, false));
    }

    @PostMapping
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<?> create(@Valid @RequestBody TxnCreateReq req) {
        return ApiResponse.of(txnBehavierService.create(null, req));
    }

    @PatchMapping("/{id}")
    public ApiResponse<Void> update(
            @PathVariable UUID id,
            @Valid @RequestBody TxnUpdateReq req) {
        txnBehavierService.update(id, req, false);
        return ApiResponse.of(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> softDelete(@PathVariable UUID id) {
        txnBehavierService.softDelete(id, false);
        return ApiResponse.of(null);
    }

    // luồng quản trị viên
    @PreAuthorize("hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).TXN_UPDATE.name())")
    @PostMapping("/admin")
    public ApiResponse<?> adminCreate(
            @RequestParam(name = "user_id") UUID userId,
            @Valid @RequestBody TxnCreateReq req) {
        return ApiResponse.of(txnBehavierService.create(userId, req));
    }

    @PreAuthorize("hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).TXN_READ.name())")
    @GetMapping("/admin")
    public ApiResponse<?> adminGets(
            @ModelAttribute TxnFilterReq filters,
            Pageable page) {
        return ApiResponse.of(txnBehavierService.gets(filters, page, true));
    }

    @PreAuthorize("hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).TXN_READ.name())")
    @GetMapping("/admin/{id}")
    public ApiResponse<?> adminDetail(@PathVariable UUID id) {
        return ApiResponse.of(txnBehavierService.get(id, true));
    }

    @PreAuthorize("hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).TXN_UPDATE.name())")
    @PatchMapping("/admin")
    public ApiResponse<?> adminUpdate(
            @RequestParam(name = "txn_id") UUID txnId,
            @Valid @RequestBody TxnUpdateReq req) {
        txnBehavierService.update(txnId, req, true);

        return ApiResponse.of(null);
    }

    @PreAuthorize("hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).TXN_DELETE.name())")
    @DeleteMapping("/admin/{id}")
    public ApiResponse<?> adminDelete(@PathVariable UUID id) {
        txnBehavierService.softDelete(id, true);
        return ApiResponse.of(null);
    }
}

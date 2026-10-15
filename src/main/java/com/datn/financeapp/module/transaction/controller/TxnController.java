package com.datn.financeapp.module.transaction.controller;

import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.transaction.dto.request.TxnCreateReq;
import com.datn.financeapp.module.transaction.dto.request.TxnFilterRequest;
import com.datn.financeapp.module.transaction.dto.request.TxnUpdateRequest;
import com.datn.financeapp.module.transaction.service.TxnBehavierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
            @ModelAttribute TxnFilterRequest filters,
            @PageableDefault(sort = "date", direction = Sort.Direction.DESC) Pageable page) {
        return ApiResponse.of(txnBehavierService.gets(filters, page));
    }

    @GetMapping("/{id}")
    public ApiResponse<?> detail(@PathVariable UUID id) {
        return ApiResponse.of(txnBehavierService.detail(id, null));
    }

    @PostMapping
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<?> create(@Valid @RequestBody TxnCreateReq req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(txnBehavierService.create(userId, req));
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(
            @PathVariable UUID id,
            @Valid @RequestBody TxnUpdateRequest req) {
        return ApiResponse.of(txnBehavierService.update(id, req));
    }

    @DeleteMapping("/bulk")
    public ApiResponse<Void> bulkDelete(@RequestBody List<UUID> ids) {
        txnBehavierService.bulkDelete(null, ids);
        return ApiResponse.of(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> softDelete(@PathVariable UUID id) {
        txnBehavierService.softDelete(null, id);
        return ApiResponse.of(null);
    }

    // luồng quản trị viên

    @PreAuthorize("hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).TXN_READ.getValue())")
    @GetMapping("/admin/{id}")
    public ApiResponse<?> adminDetail(
            @PathVariable UUID id,
            @RequestParam(name = "user_id", required = false) UUID userId) {
        return ApiResponse.of(txnBehavierService.detail(id, userId));
    }

    @PreAuthorize("hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).TXN_DELETE.getValue())")
    @DeleteMapping("/admin/{id}")
    public ApiResponse<?> adminDelete(
            @PathVariable UUID id,
            @RequestParam(name = "user_id") UUID userId) {
        txnBehavierService.softDelete(userId, id);
        return ApiResponse.of(null);
    }
}

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

import java.util.UUID;

/**
 * REST Controller quản lý các thao tác truy vấn, tạo mới, cập nhật và xóa mềm giao dịch cá nhân.
 */
@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TxnController {

    private final TxnBehavierService txnBehavierService;

    @GetMapping
    public ApiResponse<?> gets(
            @ModelAttribute TxnFilterRequest filters,
            @PageableDefault(sort = "date", direction = Sort.Direction.DESC) Pageable page) {
        return ApiResponse.of(txnBehavierService.gets(filters, page));
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

    @DeleteMapping("/{id}")
    public ApiResponse<Void> softDelete(@PathVariable UUID id) {
        txnBehavierService.softDelete(id);
        return ApiResponse.of(null);
    }
}

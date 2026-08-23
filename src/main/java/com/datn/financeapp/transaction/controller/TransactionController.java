package com.datn.financeapp.transaction.controller;

import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.transaction.dto.CreateTransactionRequest;
import com.datn.financeapp.transaction.dto.CreateTransactionResponse;
import com.datn.financeapp.transaction.dto.DeleteTransactionResponse;
import com.datn.financeapp.transaction.dto.DuplicateTransactionRequest;
import com.datn.financeapp.transaction.dto.UpdateTransactionRequest;
import com.datn.financeapp.transaction.service.TransactionService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * CRUD giao dịch đơn lẻ (TXN-03, TXN-05, TXN-06, TXN-07, api/04-GIAO-DICH.md mục 4/8/9/10).
 * POST tạo mới và duplicate gắn {@code @Idempotent} (D-11); PUT/DELETE KHÔNG gắn — cùng lý do
 * {@code WalletController.update}/{@code delete} không gắn (PUT ghi đè, DELETE tự idempotent
 * theo CORE-06).
 */
@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateTransactionResponse> create(@Valid @RequestBody CreateTransactionRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(transactionService.create(userId, req));
    }

    @PutMapping("/{id}")
    public ApiResponse<CreateTransactionResponse> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateTransactionRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(transactionService.update(userId, id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<DeleteTransactionResponse> delete(@PathVariable UUID id) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(transactionService.delete(userId, id));
    }

    @PostMapping("/{id}/duplicate")
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateTransactionResponse> duplicate(
            @PathVariable UUID id, @RequestBody(required = false) DuplicateTransactionRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(transactionService.duplicate(userId, id, req));
    }
}

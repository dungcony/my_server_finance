package com.datn.financeapp.group.controller;

import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionBulkReviewReq;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionCreateReq;
import com.datn.financeapp.group.dto.response.transaction.GroupTransactionDetailRes;
import com.datn.financeapp.group.dto.response.transaction.GroupTransactionListRes;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionFilterReq;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionUpdateReq;
import com.datn.financeapp.group.enums.TransactionStatus;
import com.datn.financeapp.group.enums.TransactionType;
import com.datn.financeapp.group.enums.MoneySource;
import com.datn.financeapp.group.service.GTransactionReviewService;
import com.datn.financeapp.group.service.GTransactionService;
import jakarta.validation.Valid;

import java.time.LocalDate;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/groups/{groupId}")
@RequiredArgsConstructor
public class GroupTransactionController {

    private final GTransactionService gTransactionService;
    private final GTransactionReviewService transactionReviewService;

    @PostMapping("/transactions")
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GroupTransactionDetailRes> create(
            @PathVariable UUID groupId,
            @Valid @RequestBody GroupTransactionCreateReq req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(gTransactionService.create(userId, groupId, req));
    }

    @GetMapping("/transactions")
    public ApiResponse<GroupTransactionListRes> list(
            @PathVariable UUID groupId,
            @RequestParam(name = "money_source", required = false) MoneySource moneySource,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(name = "transactor_id", required = false) UUID transactorId,
            @RequestParam(name = "start_date", required = false) LocalDate startDate,
            @RequestParam(name = "end_date", required = false) LocalDate endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = SecurityContextUtil.currentUserId();
        GroupTransactionFilterReq filter = new GroupTransactionFilterReq(
                moneySource, type, status, transactorId,
                null, null, startDate, endDate, page, size);
        return ApiResponse.of(gTransactionService.list(userId, groupId, filter));
    }

    @GetMapping("/transactions/{txnId}")
    public ApiResponse<GroupTransactionDetailRes> detail(
            @PathVariable UUID groupId,
            @PathVariable UUID txnId) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(gTransactionService.detail(userId, groupId, txnId));
    }

    @PutMapping("/transactions/{txnId}")
    public ApiResponse<GroupTransactionDetailRes> update(
            @PathVariable UUID groupId,
            @PathVariable UUID txnId,
            @Valid @RequestBody GroupTransactionUpdateReq req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(gTransactionService.update(userId, groupId, txnId, req));
    }

    @DeleteMapping("/transactions/{txnId}")
    public ApiResponse<Void> delete(
            @PathVariable UUID groupId,
            @PathVariable UUID txnId) {
        UUID userId = SecurityContextUtil.currentUserId();
        gTransactionService.delete(userId, groupId, txnId);
        return ApiResponse.of(null);
    }

    @PostMapping("/transactions/{txnId}/confirm")
    public ApiResponse<GroupTransactionDetailRes> confirm(
            @PathVariable UUID groupId,
            @PathVariable UUID txnId) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(transactionReviewService.confirm(userId, groupId, txnId));
    }

    @PostMapping("/transactions/{txnId}/reject")
    public ApiResponse<GroupTransactionDetailRes> reject(
            @PathVariable UUID groupId,
            @PathVariable UUID txnId) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(transactionReviewService.reject(userId, groupId, txnId));
    }

    @PostMapping("/transactions/bulk-confirm")
    public ApiResponse<?> bulkConfirm(
            @PathVariable UUID groupId,
            @Valid @RequestBody GroupTransactionBulkReviewReq req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(transactionReviewService.bulkConfirm(userId, groupId, req));
    }

    @PostMapping("/transactions/bulk-reject")
    public ApiResponse<?> bulkReject(
            @PathVariable UUID groupId,
            @Valid @RequestBody GroupTransactionBulkReviewReq req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(transactionReviewService.bulkReject(userId, groupId, req));
    }

}

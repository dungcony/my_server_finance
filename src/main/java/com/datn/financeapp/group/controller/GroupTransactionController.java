package com.datn.financeapp.group.controller;

import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.group.dto.transaction.GroupRefundReq;
import com.datn.financeapp.group.dto.transaction.GroupTransactionBulkReviewReq;
import com.datn.financeapp.group.dto.transaction.GroupTransactionBulkReviewRes;
import com.datn.financeapp.group.dto.transaction.GroupTransactionCreateReq;
import com.datn.financeapp.group.dto.transaction.GroupTransactionDetailRes;
import com.datn.financeapp.group.dto.transaction.GroupTransactionFilterReq;
import com.datn.financeapp.group.dto.transaction.GroupTransactionUpdateReq;
import com.datn.financeapp.group.dto.transaction.GroupWithdrawalReq;
import com.datn.financeapp.group.enums.GroupTransactionStatus;
import com.datn.financeapp.group.enums.GroupTransactionType;
import com.datn.financeapp.group.enums.MoneySource;
import com.datn.financeapp.group.service.GroupTransactionService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
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

    private final GroupTransactionService groupTransactionService;

    @PostMapping("/transactions")
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GroupTransactionDetailRes> create(
            @PathVariable UUID groupId,
            @Valid @RequestBody GroupTransactionCreateReq req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(groupTransactionService.create(userId, groupId, req));
    }

    @GetMapping("/transactions")
    public ApiResponse<List<GroupTransactionDetailRes>> list(
            @PathVariable UUID groupId,
            @RequestParam(name = "money_source", required = false) MoneySource moneySource,
            @RequestParam(required = false) GroupTransactionType type,
            @RequestParam(required = false) GroupTransactionStatus status,
            @RequestParam(name = "user_id", required = false) UUID filterUserId,
            @RequestParam(name = "start_date", required = false) LocalDate startDate,
            @RequestParam(name = "end_date", required = false) LocalDate endDate,
            @RequestParam(name = "wallet_id", required = false) UUID walletId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = SecurityContextUtil.currentUserId();
        GroupTransactionFilterReq filter = new GroupTransactionFilterReq(
                moneySource, type, status, filterUserId,
                null, null, walletId, startDate, endDate, page, size
        );
        return ApiResponse.of(groupTransactionService.list(userId, groupId, filter));
    }

    @GetMapping("/transactions/{txnId}")
    public ApiResponse<GroupTransactionDetailRes> detail(
            @PathVariable UUID groupId,
            @PathVariable UUID txnId) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(groupTransactionService.detail(userId, groupId, txnId));
    }

    @PutMapping("/transactions/{txnId}")
    public ApiResponse<GroupTransactionDetailRes> update(
            @PathVariable UUID groupId,
            @PathVariable UUID txnId,
            @Valid @RequestBody GroupTransactionUpdateReq req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(groupTransactionService.update(userId, groupId, txnId, req));
    }

    @DeleteMapping("/transactions/{txnId}")
    public ApiResponse<Void> delete(
            @PathVariable UUID groupId,
            @PathVariable UUID txnId) {
        UUID userId = SecurityContextUtil.currentUserId();
        groupTransactionService.delete(userId, groupId, txnId);
        return ApiResponse.of(null);
    }

    @PostMapping("/transactions/{txnId}/confirm")
    public ApiResponse<GroupTransactionDetailRes> confirm(
            @PathVariable UUID groupId,
            @PathVariable UUID txnId) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(groupTransactionService.confirm(userId, groupId, txnId));
    }

    @PostMapping("/transactions/{txnId}/reject")
    public ApiResponse<GroupTransactionDetailRes> reject(
            @PathVariable UUID groupId,
            @PathVariable UUID txnId) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(groupTransactionService.reject(userId, groupId, txnId));
    }

    @PostMapping("/transactions/bulk-confirm")
    public ApiResponse<GroupTransactionBulkReviewRes> bulkConfirm(
            @PathVariable UUID groupId,
            @Valid @RequestBody GroupTransactionBulkReviewReq req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(groupTransactionService.bulkConfirm(userId, groupId, req));
    }

    @PostMapping("/transactions/bulk-reject")
    public ApiResponse<GroupTransactionBulkReviewRes> bulkReject(
            @PathVariable UUID groupId,
            @Valid @RequestBody GroupTransactionBulkReviewReq req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(groupTransactionService.bulkReject(userId, groupId, req));
    }

    @PostMapping("/refunds")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GroupTransactionDetailRes> createRefund(
            @PathVariable UUID groupId,
            @Valid @RequestBody GroupRefundReq req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(groupTransactionService.createRefund(userId, groupId, req));
    }

    @PutMapping("/refunds/{txnId}")
    public ApiResponse<GroupTransactionDetailRes> updateRefund(
            @PathVariable UUID groupId,
            @PathVariable UUID txnId,
            @Valid @RequestBody GroupRefundReq req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(groupTransactionService.updateRefund(userId, groupId, txnId, req));
    }

    @PostMapping("/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GroupTransactionDetailRes> createWithdrawal(
            @PathVariable UUID groupId,
            @Valid @RequestBody GroupWithdrawalReq req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(groupTransactionService.createWithdrawal(userId, groupId, req));
    }

    @PutMapping("/withdrawals/{txnId}")
    public ApiResponse<GroupTransactionDetailRes> updateWithdrawal(
            @PathVariable UUID groupId,
            @PathVariable UUID txnId,
            @Valid @RequestBody GroupWithdrawalReq req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(groupTransactionService.updateWithdrawal(userId, groupId, txnId, req));
    }
}

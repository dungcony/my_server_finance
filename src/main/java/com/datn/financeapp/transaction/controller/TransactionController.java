package com.datn.financeapp.transaction.controller;

import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.response.PageRequestParams;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.transaction.dto.request.BulkCreateTransactionRequest;
import com.datn.financeapp.transaction.dto.response.BulkCreateTransactionResponse;
import com.datn.financeapp.transaction.dto.request.CreateTransactionRequest;
import com.datn.financeapp.transaction.dto.response.CreateTransactionResponse;
import com.datn.financeapp.transaction.dto.response.DeleteTransactionResponse;
import com.datn.financeapp.transaction.dto.request.DuplicateTransactionRequest;
import com.datn.financeapp.transaction.dto.response.TransactionByDateResponse;
import com.datn.financeapp.transaction.dto.response.TransactionDetailResponse;
import com.datn.financeapp.transaction.dto.request.TransactionFilterRequest;
import com.datn.financeapp.transaction.dto.response.TransactionListResponse;
import com.datn.financeapp.transaction.dto.request.UpdateTransactionRequest;
import com.datn.financeapp.transaction.service.TransactionBulkService;
import com.datn.financeapp.transaction.service.TransactionService;
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

/**
 * CRUD + đọc giao dịch (TXN-01, TXN-02, TXN-03, TXN-05, TXN-06, TXN-07, TXN-08 —
 * api/04-GIAO-DICH.md mục 1/2/3/4/8/9/10). POST tạo mới và duplicate gắn {@code @Idempotent}
 * (D-11); PUT/DELETE KHÔNG gắn — cùng lý do {@code WalletController.update}/{@code delete}
 * không gắn (PUT ghi đè, DELETE tự idempotent theo CORE-06).
 *
 * <p>{@code /transactions/by-date} PHẢI khai báo TRƯỚC {@code /transactions/{id}} về mặt logic
 * route matching của Spring — thực ra Spring ưu tiên path cụ thể hơn path có biến nên thứ tự
 * khai báo method không ảnh hưởng, nhưng vẫn đặt tường minh trước cho rõ ý đồ.
 */
@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final TransactionBulkService transactionBulkService;

    @GetMapping
    public TransactionListResponse list(
            @RequestParam(name = "from_date", required = false) LocalDate fromDate,
            @RequestParam(name = "to_date", required = false) LocalDate toDate,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String type,
            @RequestParam(name = "wallet_id", required = false) UUID walletId,
            @RequestParam(name = "category_id", required = false) UUID categoryId,
            @RequestParam(required = false) String source,
            @RequestParam(name = "counts_in_report", required = false) Boolean countsInReport,
            @RequestParam(required = false) String search,
            @RequestParam(name = "min_amount", required = false) Long minAmount,
            @RequestParam(name = "max_amount", required = false) Long maxAmount,
            @RequestParam(name = "include_transfers", required = false) Boolean includeTransfers,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(name = "sort_by", required = false) String sortBy,
            @RequestParam(name = "sort_order", required = false) String sortOrder) {
        UUID userId = SecurityContextUtil.currentUserId();
        TransactionFilterRequest filters = new TransactionFilterRequest(
                fromDate,
                toDate,
                period,
                type,
                walletId,
                categoryId,
                source,
                countsInReport,
                search,
                minAmount,
                maxAmount,
                includeTransfers);
        PageRequestParams pageParams =
                PageRequestParams.of(page, pageSize, sortBy != null ? sortBy : "date", sortOrder);
        return transactionService.list(userId, filters, pageParams);
    }

    @GetMapping("/by-date")
    public ApiResponse<TransactionByDateResponse> listByDate(
            @RequestParam(name = "from_date", required = false) LocalDate fromDate,
            @RequestParam(name = "to_date", required = false) LocalDate toDate,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String type,
            @RequestParam(name = "wallet_id", required = false) UUID walletId,
            @RequestParam(name = "category_id", required = false) UUID categoryId,
            @RequestParam(required = false) String source,
            @RequestParam(name = "counts_in_report", required = false) Boolean countsInReport,
            @RequestParam(required = false) String search,
            @RequestParam(name = "min_amount", required = false) Long minAmount,
            @RequestParam(name = "max_amount", required = false) Long maxAmount,
            @RequestParam(name = "include_transfers", required = false) Boolean includeTransfers) {
        UUID userId = SecurityContextUtil.currentUserId();
        TransactionFilterRequest filters = new TransactionFilterRequest(
                fromDate,
                toDate,
                period,
                type,
                walletId,
                categoryId,
                source,
                countsInReport,
                search,
                minAmount,
                maxAmount,
                includeTransfers);
        return ApiResponse.of(transactionService.listByDate(userId, filters));
    }

    @GetMapping("/{id}")
    public ApiResponse<TransactionDetailResponse> detail(@PathVariable UUID id) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(transactionService.detail(userId, id));
    }

    @PostMapping
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateTransactionResponse> create(@Valid @RequestBody CreateTransactionRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(transactionService.create(userId, req));
    }

    @PostMapping("/bulk")
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BulkCreateTransactionResponse> createBulk(
            @Valid @RequestBody BulkCreateTransactionRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(transactionBulkService.createBulk(userId, req));
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

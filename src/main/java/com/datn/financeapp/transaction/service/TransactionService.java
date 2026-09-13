package com.datn.financeapp.transaction.service;

import com.datn.financeapp.common.response.PageRequestParams;
import com.datn.financeapp.transaction.dto.request.CreateTransactionRequest;
import com.datn.financeapp.transaction.dto.request.DuplicateTransactionRequest;
import com.datn.financeapp.transaction.dto.request.TransactionFilterRequest;
import com.datn.financeapp.transaction.dto.request.UpdateTransactionRequest;
import com.datn.financeapp.transaction.dto.response.CreateTransactionResponse;
import com.datn.financeapp.transaction.dto.response.DeleteTransactionResponse;
import com.datn.financeapp.transaction.dto.response.GeneratedTransactionResponse;
import com.datn.financeapp.transaction.dto.response.TransactionByDateResponse;
import com.datn.financeapp.transaction.dto.response.TransactionDetailResponse;
import com.datn.financeapp.transaction.dto.response.TransactionListItemResponse;
import com.datn.financeapp.transaction.dto.response.TransactionListResponse;
import com.datn.financeapp.transaction.dto.response.TransactionRefResponse;
import com.datn.financeapp.transaction.entity.Transaction;
import java.util.List;
import java.util.UUID;

// Public API của module Transaction (CRUD giao dịch thu/chi/chuyển tiền).
public interface TransactionService {

    CreateTransactionResponse create(UUID userId, CreateTransactionRequest req);

    CreateTransactionResponse update(UUID userId, UUID transactionId, UpdateTransactionRequest req);

    DeleteTransactionResponse delete(UUID userId, UUID transactionId);

    CreateTransactionResponse duplicate(UUID userId, UUID transactionId, DuplicateTransactionRequest req);

    long countByRecurringId(UUID recurringId);

    long countActiveByUserId(UUID userId);

    List<GeneratedTransactionResponse> findGeneratedByRecurringId(UUID recurringId);

    TransactionRefResponse findRefById(UUID transactionId);

    TransactionListResponse list(UUID userId, TransactionFilterRequest filters, PageRequestParams page);

    TransactionDetailResponse detail(UUID userId, UUID transactionId);

    TransactionByDateResponse listByDate(UUID userId, TransactionFilterRequest filters);

    Transaction findPersistedOrThrow(UUID transactionId);

    TransactionListItemResponse buildListItemResponse(Transaction txn);

    void validateShape(String type, UUID categoryId, UUID destinationWalletId, UUID walletId);
}

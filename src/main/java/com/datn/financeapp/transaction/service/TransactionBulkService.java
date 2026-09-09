package com.datn.financeapp.transaction.service;

import com.datn.financeapp.transaction.dto.request.BulkCreateTransactionRequest;
import com.datn.financeapp.transaction.dto.response.BulkCreateTransactionResponse;
import java.util.UUID;

// Public API của module Transaction cho thao tác tạo giao dịch hàng loạt (Bulk).
public interface TransactionBulkService {

    BulkCreateTransactionResponse createBulk(UUID userId, BulkCreateTransactionRequest req);
}

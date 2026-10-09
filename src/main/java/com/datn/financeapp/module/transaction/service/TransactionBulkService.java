package com.datn.financeapp.module.transaction.service;

import com.datn.financeapp.module.transaction.dto.request.BulkCreateTransactionRequest;
import com.datn.financeapp.module.transaction.dto.response.BulkCreateTransactionResponse;

import java.util.UUID;

/**
 * Interface định nghĩa dịch vụ tạo nhiều giao dịch hàng loạt (Bulk).
 * Cung cấp phương thức chính {@link #createBulk(UUID, BulkCreateTransactionRequest)}.
 */
public interface TransactionBulkService {

    /**
     * Thực hiện tạo hàng loạt các giao dịch trong một yêu cầu.
     *
     * @param userId ID người dùng
     * @param req    thông tin yêu cầu tạo hàng loạt giao dịch
     * @return {@link BulkCreateTransactionResponse} kết quả thực hiện hàng loạt
     */
    BulkCreateTransactionResponse createBulk(UUID userId, BulkCreateTransactionRequest req);
}

package com.datn.financeapp.transaction.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Body của POST /transactions/bulk (TXN-04, api/04-GIAO-DICH.md mục 5). Tái dùng nguyên
 * {@link CreateTransactionRequest} cho từng dòng — KHÔNG tạo DTO dòng riêng.
 */
public record BulkCreateTransactionRequest(@NotEmpty @Valid List<CreateTransactionRequest> items) {
}

package com.datn.financeapp.transaction.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Body của POST /transactions (api/04-GIAO-DICH.md mục 4). Validate chéo-field (category bắt
 * buộc/rỗng, destination bắt buộc/rỗng theo {@code type}) KHÔNG dùng annotation — Bean Validation
 * không validate chéo field được, xử lý bằng {@code if} tường minh trong {@code TransactionService}
 * (cùng khuôn {@code WalletTransferService.transfer()} tự check {@code sourceId.equals(destinationId)}).
 */
public record CreateTransactionRequest(
        @NotNull @Pattern(regexp = "^(expense|income|transfer)$") String type,
        @NotNull Long amount,
        @NotNull UUID walletId,
        UUID destinationWalletId,
        UUID categoryId,
        LocalDate date,
        @Size(max = 150) String displayName,
        @Size(max = 500) String note,
        String source,
        Boolean countsInReport,
        String receiptUrl,
        UUID draftId) {
}

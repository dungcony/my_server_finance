package com.datn.financeapp.transaction.dto.response;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Một giao dịch do tác vụ nền sinh ra, kèm {@code type} và {@code source} để bên gọi phân biệt
 * được nguồn gốc.
 *
 * <p>Rộng hơn {@link TransactionRefResponse} vì màn chi tiết khoản định kỳ cần biết mỗi lần chạy
 * là thu hay chi, và do người dùng tự ghi hay do máy sinh.
 */
public record GeneratedTransactionResponse(
        UUID id, LocalDate date, Long amount, String type, String source) {}

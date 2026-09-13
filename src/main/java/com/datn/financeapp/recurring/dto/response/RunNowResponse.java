package com.datn.financeapp.recurring.dto.response;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Phản hồi 201 của {@code POST /recurring/{id}/run-now} (api/09 mục A3).
 *
 * <p>Trả kèm {@code newBalance} để app cập nhật số dư ngay mà không phải gọi lại {@code /wallets}.
 * KHÔNG trả {@code next_run_date} mới vì run-now theo đặc tả không làm đổi lịch.
 */
public record RunNowResponse(TransactionSummary transaction, NewBalance newBalance) {

    public record TransactionSummary(UUID id, String type, Long amount, LocalDate date) {}

    public record NewBalance(UUID walletId, Long balance) {}
}

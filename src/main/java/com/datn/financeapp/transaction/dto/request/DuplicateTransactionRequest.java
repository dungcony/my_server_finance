package com.datn.financeapp.transaction.dto.request;

import java.time.LocalDate;

/**
 * Body (tuỳ chọn) của POST /transactions/{id}/duplicate (api/04-GIAO-DICH.md mục 10). Cả 2 field
 * đều optional — ghi đè nếu có, ngược lại lấy mặc định (ngày hôm nay, số tiền bản gốc).
 */
public record DuplicateTransactionRequest(Long amount, LocalDate date) {
}

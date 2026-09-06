package com.datn.financeapp.transaction.dto.response;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Tham chiếu tối thiểu tới một giao dịch — định danh, số tiền, ngày.
 *
 * <p>Dùng khi module khác cần nhắc tới giao dịch gốc trong phản hồi của mình (sổ nợ trỏ về giao
 * dịch sinh ra khoản nợ, khoản định kỳ liệt kê các lần đã chạy). Trả DTO này thay vì entity
 * {@code Transaction} để module ngoài không phụ thuộc cấu trúc bảng {@code transactions}
 * (quy tắc 11 CLAUDE.md).
 */
public record TransactionRefResponse(UUID id, Long amount, LocalDate date) {}

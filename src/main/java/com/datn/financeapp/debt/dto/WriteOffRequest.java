package com.datn.financeapp.debt.dto;

/**
 * Body {@code POST /debts/{id}/write-off} (api/08 mục 7). {@code reason} tuỳ chọn — ghi nối vào
 * {@code note} của khoản nợ để giữ lại lý do, vì schema V4 không có cột riêng cho nó.
 */
public record WriteOffRequest(String reason) {
}

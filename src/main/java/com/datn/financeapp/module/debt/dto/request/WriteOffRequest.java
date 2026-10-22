package com.datn.financeapp.module.debt.dto.request;

/**
 * Yêu cầu xóa nợ xấu (khoản nợ không thu hồi được hoặc được miễn).
 *
 * @param reason Lý do xóa nợ
 */
public record WriteOffRequest(String reason) {
}

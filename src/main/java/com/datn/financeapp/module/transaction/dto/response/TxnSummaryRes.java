package com.datn.financeapp.module.transaction.dto.response;

import com.datn.financeapp.module.transaction.enums.TxnType;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Thông tin tóm tắt của giao dịch, chỉ dùng khi cần lấy nhiều để giảm tải RAM.
 * Muốn đủ chi tiết (ghi chú, hóa đơn, ví, danh mục) dùng {@link TxnDetailResponse}.
 *
 * @param id         ID giao dịch
 * @param type       loại giao dịch
 * @param amount     số tiền (luôn dương, chiều tiền suy từ {@code type})
 * @param date       ngày phát sinh
 * @param name       tên hiển thị
 * @param walletId   ID ví phát sinh
 * @param categoryId ID danh mục
 */
public record TxnSummaryRes(
        UUID id,
        TxnType type,
        Long amount,
        LocalDate date,
        String name,
        UUID walletId,
        UUID categoryId) {
}

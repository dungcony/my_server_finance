package com.datn.financeapp.module.debt.dto.response;

import java.util.UUID;

/**
 * Thông tin phản hồi tạo mới khoản nợ.
 *
 * @param debt              Thông tin tóm tắt khoản nợ
 * @param originTransaction Giao dịch gốc được tự động tạo
 * @param newBalance        Số dư mới của ví liên quan
 */
public record CreateDebtResponse(
        DebtListItemResponse debt, OriginTransaction originTransaction, NewBalance newBalance) {

    /**
     * Giao dịch gốc tự động sinh.
     *
     * @param id     ID giao dịch
     * @param type   Loại giao dịch
     * @param amount Số tiền (Đơn vị: VND)
     */
    public record OriginTransaction(UUID id, String type, Long amount) {
    }

    /**
     * Số dư mới của ví.
     *
     * @param walletId ID ví
     * @param balance  Số dư mới (Đơn vị: VND)
     */
    public record NewBalance(UUID walletId, Long balance) {
    }
}

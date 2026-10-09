package com.datn.financeapp.module.recurring.dto.response;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Phản hồi khi thực hiện kích hoạt chạy ngay một giao dịch định kỳ.
 *
 * @param transaction thông tin tóm tắt giao dịch mới vừa phát sinh
 * @param newBalance  thông tin số dư mới của ví
 */
public record RunNowResponse(TransactionSummary transaction, NewBalance newBalance) {

    /**
     * Tóm tắt thông tin giao dịch phát sinh.
     *
     * @param id     ID giao dịch phát sinh
     * @param type   loại giao dịch
     * @param amount số tiền
     * @param date   ngày thực hiện
     */
    public record TransactionSummary(UUID id, String type, Long amount, LocalDate date) {
    }

    /**
     * Thông tin số dư mới sau khi sinh giao dịch.
     *
     * @param walletId ID ví
     * @param balance  số dư mới
     */
    public record NewBalance(UUID walletId, Long balance) {
    }
}

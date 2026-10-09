package com.datn.financeapp.module.debt.dto.response;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Thông tin phản hồi ghi nhận đợt thanh toán nợ thành công.
 *
 * @param payment     Thông tin đợt thanh toán vừa ghi nhận
 * @param debt        Tiến độ khoản nợ cập nhật mới nhất
 * @param transaction Giao dịch phát sinh tự động
 * @param newBalance  Số dư mới của ví liên quan
 */
public record CreatePaymentResponse(
        Payment payment, DebtProgress debt, TransactionSummary transaction, NewBalance newBalance) {

    /**
     * Thông tin đợt thanh toán.
     *
     * @param id       ID đợt thanh toán
     * @param amount   Số tiền trả (Đơn vị: VND)
     * @param paidDate Ngày trả
     * @param note     Ghi chú
     */
    public record Payment(UUID id, Long amount, LocalDate paidDate, String note) {
    }

    /**
     * Tiến độ khoản nợ cập nhật.
     *
     * @param paidAmount      Tổng số tiền đã trả (Đơn vị: VND)
     * @param remainingAmount Số tiền còn nợ (Đơn vị: VND)
     * @param status          Trạng thái khoản nợ
     */
    public record DebtProgress(Long paidAmount, Long remainingAmount, String status) {
    }

    /**
     * Giao dịch phát sinh.
     *
     * @param id     ID giao dịch
     * @param type   Loại giao dịch
     * @param amount Số tiền (Đơn vị: VND)
     */
    public record TransactionSummary(UUID id, String type, Long amount) {
    }

    /**
     * Số dư ví cập nhật.
     *
     * @param walletId ID ví
     * @param balance  Số dư mới (Đơn vị: VND)
     */
    public record NewBalance(UUID walletId, Long balance) {
    }
}

package com.datn.financeapp.module.wallet.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Một lần job đối chiếu sửa số dư của ví (một dòng {@code reconcil_history}).
 *
 * @param id            ID bản ghi lịch sử
 * @param wallet        ví đã được sửa
 * @param walletBalance số dư ví trước khi đối chiếu
 * @param totalTxn      số dư đúng, bằng tổng giao dịch chưa xoá của ví
 * @param reconciledAt  thời điểm đối chiếu
 */
public record ReconcileRes(
        UUID id,
        WalletSumaryRes wallet,
        Long walletBalance,
        Long totalTxn,
        Instant reconciledAt
) {
}

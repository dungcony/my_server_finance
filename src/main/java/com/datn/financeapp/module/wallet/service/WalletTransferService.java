package com.datn.financeapp.module.wallet.service;

import com.datn.financeapp.module.wallet.dto.response.ReconcileResponse;

import java.util.UUID;

/**
 * Interface dịch vụ đối chiếu số dư ví.
 * Cung cấp các phương thức {@link #reconcile} và {@link #reconcileAllWallets}.
 */
public interface WalletTransferService {

    /**
     * Đối chiếu số dư ví với tổng các giao dịch liên quan.
     *
     * @param userId   ID người dùng
     * @param walletId ID ví
     * @param autoFix  có tự động sửa chênh lệch hay không
     * @return DTO phản hồi kết quả đối chiếu
     */
    ReconcileResponse reconcile(UUID userId, UUID walletId, boolean autoFix);

    /**
     * Đối chiếu số dư cho toàn bộ ví trong hệ thống (dùng cho công việc định kỳ).
     */
    void reconcileAllWallets();
}

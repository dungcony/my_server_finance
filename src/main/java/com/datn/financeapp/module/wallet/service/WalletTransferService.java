package com.datn.financeapp.module.wallet.service;

import com.datn.financeapp.module.wallet.dto.request.AdjustBalanceRequest;
import com.datn.financeapp.module.wallet.dto.request.TransferRequest;
import com.datn.financeapp.module.wallet.dto.response.AdjustBalanceResponse;
import com.datn.financeapp.module.wallet.dto.response.ReconcileResponse;
import com.datn.financeapp.module.wallet.dto.response.TransferResponse;

import java.util.UUID;

/**
 * Interface dịch vụ xử lý chuyển tiền giữa các ví, điều chỉnh số dư và đối chiếu số dư.
 * Cung cấp các phương thức {@link #transfer}, {@link #adjustBalance}, {@link #reconcile} và {@link #reconcileAllWallets}.
 */
public interface WalletTransferService {

    /**
     * Chuyển tiền từ ví nguồn sang ví đích.
     *
     * @param userId ID người dùng
     * @param req    DTO chứa thông tin chuyển tiền
     * @return DTO phản hồi kết quả chuyển tiền
     */
    TransferResponse transfer(UUID userId, TransferRequest req);

    /**
     * Điều chỉnh số dư của ví về giá trị thực tế.
     *
     * @param userId   ID người dùng
     * @param walletId ID ví
     * @param req      DTO chứa số dư thực tế và lý do
     * @return DTO phản hồi kết quả điều chỉnh
     */
    AdjustBalanceResponse adjustBalance(UUID userId, UUID walletId, AdjustBalanceRequest req);

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

package com.datn.financeapp.group.service;

import com.datn.financeapp.group.dto.request.wallet.GroupWalletReconcileReq;
import com.datn.financeapp.group.dto.response.wallet.GroupWalletReconcileRes;
import com.datn.financeapp.group.dto.response.wallet.GroupWalletRes;
import com.datn.financeapp.group.dto.request.wallet.GroupWalletUpdateReq;
import java.util.UUID;

/**
 * Service quản lý quỹ duy nhất của nhóm tài chính (Group Wallet/Fund).
 */
public interface GroupWalletService {

    /**
     * Lấy thông tin quỹ chung của nhóm.
     *
     * @param userId  ID người dùng yêu cầu (phải là thành viên nhóm)
     * @param groupId ID nhóm
     * @return Thông tin chi tiết quỹ nhóm (ID ví, người giữ quỹ, số dư hiện tại, trạng thái)
     */
    GroupWalletRes getWallet(UUID userId, UUID groupId);

    /**
     * Cập nhật thông tin quỹ nhóm (chuyển giao người giữ quỹ/thủ quỹ).
     * <p>Yêu cầu quyền: Trưởng nhóm (OWNER).</p>
     *
     * @param userId  ID người thực hiện (phải là Owner)
     * @param groupId ID nhóm
     * @param req     Dữ liệu cập nhật (ID thành viên giữ quỹ mới...)
     * @return Thông tin quỹ sau khi cập nhật
     */
    GroupWalletRes updateFund(UUID userId, UUID groupId, GroupWalletUpdateReq req);

    /**
     * Thực hiện kiểm kê/đối soát số dư quỹ (Reconciliation).
     *
     * @param userId  ID người thực hiện kiểm kê
     * @param groupId ID nhóm
     * @param req     Dữ liệu kiểm kê chứa số dư thực tế và ghi chú
     * @return Kết quả đối soát
     */
    GroupWalletReconcileRes reconcileFund(UUID userId, UUID groupId, GroupWalletReconcileReq req);

    /**
     * Cộng hoặc trừ một lượng tiền trực tiếp vào số dư quỹ (Dùng nội bộ khi giao dịch nhóm CONFIRMED/ROLLBACK).
     *
     * @param walletId ID ví nhóm cần điều chỉnh
     * @param delta    Lượng tiền thay đổi (dương là cộng tiền vào quỹ, âm là trừ tiền khỏi quỹ)
     */
    void adjustBalance(UUID walletId, Long delta);
}

package com.datn.financeapp.module.group.service;

import com.datn.financeapp.module.group.dto.request.fund.FundReconcileReq;
import com.datn.financeapp.module.group.dto.request.fund.FundKepperUpdateReq;
import com.datn.financeapp.module.group.dto.response.fund.FundRes;
import com.datn.financeapp.module.group.dto.response.fund.GroupFundReconcileRes;

import java.time.Instant;
import java.util.UUID;

/**
 * Service quản lý quỹ duy nhất của nhóm tài chính (Group Fund).
 */
public interface FundService {

    FundRes create(UUID groupId, UUID operatorId);

    /**
     * Cập nhật thông tin quỹ nhóm (chuyển giao người giữ quỹ/thủ quỹ).
     * <p>Yêu cầu quyền: Trưởng nhóm (OWNER).</p>
     *
     * @param groupId ID nhóm
     * @param req     Dữ liệu cập nhật (ID thành viên giữ quỹ mới...)
     * @return Thông tin quỹ sau khi cập nhật
     */
    FundRes updateFundKeepper(UUID groupId, FundKepperUpdateReq req);

    /**
     * Thực hiện kiểm kê/đối soát số dư quỹ (Reconciliation).
     *
     * @param groupId ID nhóm
     * @param req     Dữ liệu kiểm kê chứa số dư thực tế và ghi chú
     * @return Kết quả đối soát
     */
    GroupFundReconcileRes reconcileFund(UUID groupId, FundReconcileReq req);

    /**
     * Cộng hoặc trừ một lượng tiền trực tiếp vào số dư quỹ (Dùng nội bộ khi giao dịch nhóm CONFIRMED/ROLLBACK).
     *
     * @param fundId ID quỹ nhóm cần điều chỉnh
     * @param delta  Lượng tiền thay đổi (dương là cộng tiền vào quỹ, âm là trừ tiền khỏi quỹ)
     */
    void adjustBalance(UUID fundId, Long delta);

}

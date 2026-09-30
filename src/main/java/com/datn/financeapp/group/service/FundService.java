package com.datn.financeapp.group.service;

import com.datn.financeapp.group.dto.request.fund.GroupFundReconcileReq;
import com.datn.financeapp.group.dto.request.fund.GroupFundUpdateReq;
import com.datn.financeapp.group.dto.response.fund.GroupFundReconcileRes;
import com.datn.financeapp.group.dto.response.fund.GroupFundRes;

import java.time.Instant;
import java.util.UUID;

/**
 * Service quản lý quỹ duy nhất của nhóm tài chính (Group Fund).
 * <p>
 * Các hàm trong interface:
 * <ul>
 *   <li>{@link #addFund}: Tạo quỹ mới. Input: groupId, heldByUserId, createdAt. Output: GroupFundRes.</li>
 *   <li>{@link #getFund}: Lấy thông tin quỹ (cả 2 overload). Input: operatorId (optional), groupId. Output: GroupFundRes.</li>
 *   <li>{@link #updateFund}: Cập nhật người giữ quỹ. Input: operatorId, groupId, req. Output: GroupFundRes.</li>
 *   <li>{@link #reconcileFund}: Kiểm kê/đối soát quỹ. Input: operatorId, groupId, req. Output: GroupFundReconcileRes.</li>
 *   <li>{@link #adjustBalance}: Điều chỉnh số dư trực tiếp. Input: fundId, delta. Output: void.</li>
 * </ul>
 * </p>
 */
public interface FundService {

    GroupFundRes addFund(UUID groupId, UUID heldByUserId, Instant createdAt);

    /**
     * Lấy thông tin quỹ chung của nhóm.
     *
     * @param operatorId ID thành viên yêu cầu
     * @param groupId    ID nhóm
     * @return Thông tin chi tiết quỹ nhóm (ID quỹ, người giữ quỹ, số dư hiện tại, trạng thái)
     */
    GroupFundRes getFund(UUID operatorId, UUID groupId);

    /**
     * Admin Lấy thông tin quỹ chung của nhóm.
     *
     * @param groupId ID nhóm
     * @return Thông tin chi tiết quỹ nhóm (ID quỹ, người giữ quỹ, số dư hiện tại, trạng thái)
     */
    GroupFundRes getFund(UUID groupId);

    /**
     * Cập nhật thông tin quỹ nhóm (chuyển giao người giữ quỹ/thủ quỹ).
     * <p>Yêu cầu quyền: Trưởng nhóm (OWNER).</p>
     *
     * @param operatorId ID người thực hiện (phải là Owner)
     * @param groupId    ID nhóm
     * @param req        Dữ liệu cập nhật (ID thành viên giữ quỹ mới...)
     * @return Thông tin quỹ sau khi cập nhật
     */
    GroupFundRes updateFund(UUID operatorId, UUID groupId, GroupFundUpdateReq req);

    /**
     * Thực hiện kiểm kê/đối soát số dư quỹ (Reconciliation).
     *
     * @param operatorId ID người thực hiện kiểm kê
     * @param groupId    ID nhóm
     * @param req        Dữ liệu kiểm kê chứa số dư thực tế và ghi chú
     * @return Kết quả đối soát
     */
    GroupFundReconcileRes reconcileFund(UUID operatorId, UUID groupId, GroupFundReconcileReq req);

    /**
     * Cộng hoặc trừ một lượng tiền trực tiếp vào số dư quỹ (Dùng nội bộ khi giao dịch nhóm CONFIRMED/ROLLBACK).
     *
     * @param fundId ID quỹ nhóm cần điều chỉnh
     * @param delta  Lượng tiền thay đổi (dương là cộng tiền vào quỹ, âm là trừ tiền khỏi quỹ)
     */
    void adjustBalance(UUID fundId, Long delta);

}

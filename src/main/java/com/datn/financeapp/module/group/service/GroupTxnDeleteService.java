package com.datn.financeapp.module.group.service;

import java.util.UUID;

public interface GroupTxnDeleteService {
    /**
     * Xóa mềm (soft-delete) một giao dịch nhóm bằng cách đánh dấu thời điểm
     * {@code deletedAt}.
     * <p>
     * <b>Phân quyền:</b> Chỉ Trưởng nhóm (OWNER).
     * <br>
     * <b>Hoàn tác quỹ:</b> Nếu giao dịch đã {@code CONFIRMED}, số tiền sẽ được tự
     * động hoàn tác (reversal) vào quỹ nhóm.
     * </p>
     *
     * @param groupId       ID nhóm
     * @param transactionId ID giao dịch cần xóa
     */
    void softDelete(UUID groupId, UUID transactionId);

}

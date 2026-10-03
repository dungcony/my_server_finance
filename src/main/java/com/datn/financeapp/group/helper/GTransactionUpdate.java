package com.datn.financeapp.group.helper;

import com.datn.financeapp.group.dto.request.transaction.GroupTransactionUpdateReq;
import com.datn.financeapp.group.entity.GTransaction;
import com.datn.financeapp.group.enums.GTransactionType;
import com.datn.financeapp.group.enums.GTransactionStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Interface chiến lược cập nhật thông tin giao dịch tài chính nhóm.
 * <p>
 * Chi tiết các phương thức:
 * <ul>
 *   <li>{@link #supports(GTransactionType)}: Kiểm tra loại giao dịch hỗ trợ.</li>
 *   <li>{@link #update(GTransaction, UUID, UUID, GroupTransactionUpdateReq, MemberAuthInfo)}: Cập nhật thực thể theo nghiệp vụ riêng.</li>
 *   <li>{@link #applyBaseUpdate(GTransaction, UUID, GroupTransactionUpdateReq, MemberAuthInfo)}: Cập nhật an toàn các trường chung và trạng thái duyệt.</li>
 * </ul>
 * </p>
 */
public interface GTransactionUpdate {

    // kiểm tra strategy có phụ trách loại giao dịch này hay không
    boolean supports(GTransactionType type);

    // thực hiện cập nhật thực thể giao dịch theo nghiệp vụ riêng
    void update(GTransaction txn, UUID operatorId, UUID groupId, GroupTransactionUpdateReq req, MemberAuthInfo authInfo);

    // cập nhật an toàn các trường chung, tránh ghi đè dữ liệu cũ bằng null
    default void applyBaseUpdate(GTransaction txn, UUID operatorId, GroupTransactionUpdateReq req, MemberAuthInfo authInfo) {
        boolean isAutoApprove = authInfo.isOwner() || authInfo.isTreasurer();
        GTransactionStatus newStatus = isAutoApprove ? GTransactionStatus.CONFIRMED : GTransactionStatus.PENDING;
        Instant now = Instant.now();

        txn.setStatus(newStatus);
        txn.setReviewedBy(isAutoApprove ? operatorId : null);
        txn.setReviewedAt(isAutoApprove ? now : null);
        txn.setUpdatedAt(now);

        // chỉ set dữ liệu mới nếu client có truyền lên
        if (req.amount() != null) {
            txn.setAmount(req.amount());
        }
        if (req.occurredAt() != null || req.date() != null) {
            txn.setOccurredAt(req.resolveOccurredAt());
        }
        if (req.note() != null) {
            txn.setNote(req.note().trim());
        }
    }
}

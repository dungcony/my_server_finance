package com.datn.financeapp.group.service.impl.strategy.update;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionUpdateReq;
import com.datn.financeapp.group.entity.GTransaction;
import com.datn.financeapp.group.enums.MoneySource;
import com.datn.financeapp.group.enums.TransactionType;
import com.datn.financeapp.group.helper.MemberAuthInfo;
import com.datn.financeapp.group.service.GTransactionUpdateStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Chiến lược cập nhật giao dịch điều chỉnh số dư quỹ (ADJUSTMENT_UP,
 * ADJUSTMENT_DOWN).
 * <p>
 * Chi tiết các phương thức:
 * <ul>
 * <li>{@link #supports(TransactionType)}: Kiểm tra loại ADJUSTMENT_UP hoặc
 * ADJUSTMENT_DOWN.</li>
 * <li>{@link #update(GTransaction, UUID, UUID, GroupTransactionUpdateReq, MemberAuthInfo)}:
 * Kiểm tra lý do điều chỉnh bắt buộc, gắn nguồn tiền quỹ nhóm.</li>
 * </ul>
 * </p>
 */
@Component
@RequiredArgsConstructor
public class AdjustmentUpdateStrategy implements GTransactionUpdateStrategy {

    @Override
    public boolean supports(TransactionType type) {
        return type == TransactionType.ADJUSTMENT_UP || type == TransactionType.ADJUSTMENT_DOWN;
    }

    @Override
    public void update(GTransaction txn, UUID operatorId, UUID groupId, GroupTransactionUpdateReq req,
                       MemberAuthInfo authInfo) {
        // lý do điều chỉnh là bắt buộc
        String note = req.note() != null ? req.note().trim() : txn.getNote();
        if (note == null || note.isEmpty()) {
            throw new BusinessException(ErrorCode.ADJUSTMENT_REASON_REQUIRED);
        }

        // cập nhật các trường chung và trạng thái kiểm duyệt
        applyBaseUpdate(txn, operatorId, req, authInfo);

        // điều chỉnh số dư luôn gắn với quỹ nhóm
        txn.setMoneySource(MoneySource.FUND);
        txn.setTransactorId(operatorId);
        txn.setCategoryId(null);
        txn.getParticipants().clear();
    }
}

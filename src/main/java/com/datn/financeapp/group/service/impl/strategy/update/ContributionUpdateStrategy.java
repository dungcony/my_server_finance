package com.datn.financeapp.group.service.impl.strategy.update;

import com.datn.financeapp.group.dto.request.transaction.GroupTransactionUpdateReq;
import com.datn.financeapp.group.entity.GTransaction;
import com.datn.financeapp.group.enums.MoneySource;
import com.datn.financeapp.group.enums.TransactionType;
import com.datn.financeapp.group.helper.MemberAuthInfo;
import com.datn.financeapp.group.service.GTransactionUpdateStrategy;
import com.datn.financeapp.group.validator.GroupTransactionPaticipantValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Chiến lược cập nhật giao dịch nộp quỹ nhóm (CONTRIBUTION).
 * <p>
 * Chi tiết các phương thức:
 * <ul>
 * <li>{@link #supports(TransactionType)}: Kiểm tra loại CONTRIBUTION.</li>
 * <li>{@link #update(GTransaction, UUID, UUID, GroupTransactionUpdateReq, MemberAuthInfo)}:
 * Cập nhật người nộp tiền, ép nguồn tiền ví cá nhân và dọn dẹp người chia
 * tiền.</li>
 * </ul>
 * </p>
 */
@Component
@RequiredArgsConstructor
public class ContributionUpdateStrategy implements GTransactionUpdateStrategy {

    private final GroupTransactionPaticipantValidator transactionValidator;

    @Override
    public boolean supports(TransactionType type) {
        return type == TransactionType.CONTRIBUTION;
    }

    @Override
    public void update(GTransaction txn, UUID operatorId, UUID groupId, GroupTransactionUpdateReq req,
                       MemberAuthInfo authInfo) {
        // cập nhật các trường chung và trạng thái kiểm duyệt
        applyBaseUpdate(txn, operatorId, req, authInfo);

        // đóng góp quỹ luôn lấy tiền từ ví cá nhân
        txn.setMoneySource(MoneySource.PERSONAL);

        // xác định người đóng góp
        UUID contributorId = req.userId() != null ? req.userId() : txn.getTransactorId();
        transactionValidator.validTransactorAndParticipants(groupId, contributorId, List.of(), txn.getAmount());

        txn.setTransactorId(contributorId);
        txn.setCategoryId(null);
        txn.getParticipants().clear();
    }
}

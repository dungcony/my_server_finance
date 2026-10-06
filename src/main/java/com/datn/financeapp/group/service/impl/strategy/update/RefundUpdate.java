package com.datn.financeapp.group.service.impl.strategy.update;

import com.datn.financeapp.group.dto.request.transaction.GroupTransactionUpdateReq;
import com.datn.financeapp.group.entity.GTransaction;
import com.datn.financeapp.group.enums.MoneySource;
import com.datn.financeapp.group.enums.GTransactionType;
import com.datn.financeapp.group.helper.BalanceCalculator;
import com.datn.financeapp.group.helper.MemberAuthInfo;
import com.datn.financeapp.group.helper.MemberBalances;
import com.datn.financeapp.group.helper.GTransactionUpdate;
import com.datn.financeapp.group.service.MemberBalanceService;
import com.datn.financeapp.group.validator.GroupTransactionPaticipantValidator;
import com.datn.financeapp.group.validator.GroupTransactionTypeValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Chiến lược cập nhật giao dịch quỹ trả tiền cho thành viên (REFUND).
 * <p>
 * Chi tiết các phương thức:
 * <ul>
 * <li>{@link #supports(GTransactionType)}: Kiểm tra loại REFUND.</li>
 * <li>{@link #update(GTransaction, UUID, UUID, GroupTransactionUpdateReq, MemberAuthInfo)}:
 * Kiểm lại hạn mức khi số tiền tăng hoặc đổi người nhận, cập nhật thành viên nhận tiền,
 * ép nguồn tiền quỹ nhóm và dọn dẹp người chia tiền.</li>
 * </ul>
 * </p>
 */
@Component
@RequiredArgsConstructor
public class RefundUpdate implements GTransactionUpdate {

    private final GroupTransactionPaticipantValidator transactionValidator;
    private final MemberBalanceService memberBalanceService;

    @Override
    public boolean supports(GTransactionType type) {
        return type == GTransactionType.REFUND;
    }

    @Override
    public void update(GTransaction txn, UUID operatorId, UUID groupId, GroupTransactionUpdateReq req,
                       MemberAuthInfo authInfo) {
        // xác định thành viên nhận hoàn tiền và số tiền mới
        UUID targetUserId = req.transactorId() != null ? req.transactorId() : txn.getTransactorId();
        long newAmount = req.amount() != null ? req.amount() : txn.getAmount();
        transactionValidator.validTransactorAndParticipants(groupId, targetUserId, List.of(), newAmount);

        // kiểm hạn mức trước khi ghi giá trị mới vào giao dịch
        if (newAmount > txn.getAmount() || !targetUserId.equals(txn.getTransactorId())) {
            validateLimitExcludingSelf(txn, groupId, targetUserId, newAmount);
        }

        // cập nhật các trường chung và trạng thái kiểm duyệt
        applyBaseUpdate(txn, operatorId, req, authInfo);

        // hoàn tiền luôn lấy tiền từ quỹ nhóm
        txn.setMoneySource(MoneySource.FUND);

        txn.setTransactorId(targetUserId);
        txn.setCategoryId(null);
        txn.getParticipants().clear();
    }

    // tính số dư hiện tại của người nhận khi coi như khoản đang sửa chưa từng tồn tại rồi kiểm hạn mức với số tiền mới
    private void validateLimitExcludingSelf(GTransaction txn, UUID groupId, UUID targetUserId, long newAmount) {
        // khoá dòng của cả người nhận cũ lẫn mới, rồi trừ ảnh hưởng của chính khoản đang sửa ra khỏi số dư
        MemberBalances balances = memberBalanceService
                .getBalancesForUpdate(groupId, Stream.of(txn.getTransactorId(), targetUserId).distinct().toList())
                .minus(BalanceCalculator.effectOf(txn));

        GroupTransactionTypeValidator.validRefundLimit(targetUserId, newAmount, balances);
    }
}

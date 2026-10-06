
package com.datn.financeapp.group.service.impl.strategy.create;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionCreateReq;
import com.datn.financeapp.group.entity.GTransaction;
import com.datn.financeapp.group.enums.*;
import com.datn.financeapp.group.helper.MemberBalances;
import com.datn.financeapp.group.helper.GTransactionBuilder;
import com.datn.financeapp.group.service.MemberBalanceService;
import com.datn.financeapp.group.validator.GroupTransactionTypeValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Chiến lược xây dựng giao dịch quỹ trả tiền cho thành viên (REFUND).
 * <p>
 * Chi tiết các phương thức:
 * <ul>
 * <li>{@link #supports(GTransactionType)}: Xác nhận hỗ trợ loại REFUND.</li>
 * <li>{@link #build(UUID, UUID, GroupTransactionCreateReq, GTransactionStatus, boolean)}:
 * Kiểm tra nguồn tiền FUND, tính số dư còn lại của người nhận để đối soát hạn mức
 * hoàn trả và hoàn thiện thực thể giao dịch.</li>
 * </ul>
 * </p>
 */
@Component
@RequiredArgsConstructor
public class RefundTransaction implements GTransactionBuilder {

    private final MemberBalanceService memberBalanceService;

    @Override
    public boolean supports(GTransactionType type) {
        return type == GTransactionType.REFUND;
    }

    @Override
    public GTransactionStatus determineStatus(com.datn.financeapp.group.helper.MemberAuthInfo authInfo) {
        if (authInfo.isOwner() || authInfo.isTreasurer()) {
            return GTransactionStatus.CONFIRMED;
        }
        throw new com.datn.financeapp.common.exception.BusinessException(com.datn.financeapp.common.exception.ErrorCode.GROUP_TXN_TYPE_NOT_ALLOWED);
    }

    @Override
    public GTransaction build(UUID operatorId, UUID groupId, GroupTransactionCreateReq req,
                              GTransactionStatus status, boolean isSettlementEnabled) {
        MoneySource source = req.resolveMoneySource();
        if (source != MoneySource.FUND) {
            throw new BusinessException(ErrorCode.GROUP_TXN_MONEY_SOURCE_INVALID);
        }

        // đọc số dư hiện tại của người nhận kèm khoá dòng để kiểm hạn mức hoàn trả
        MemberBalances mb = memberBalanceService.getBalancesForUpdate(groupId, List.of(req.transactorId()));

        GroupTransactionTypeValidator.validRefundLimit(req.transactorId(), req.amount(), mb);

        return baseBuilder(operatorId, groupId, req, source, status)
                .categoryId(null)
                .participants(List.of())
                .build();
    }
}

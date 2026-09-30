
package com.datn.financeapp.group.service.impl.strategy.create;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionCreateReq;
import com.datn.financeapp.group.entity.GTransaction;
import com.datn.financeapp.group.entity.Member;
import com.datn.financeapp.group.enums.MemberStatus;
import com.datn.financeapp.group.enums.MoneySource;
import com.datn.financeapp.group.enums.TransactionStatus;
import com.datn.financeapp.group.enums.TransactionType;
import com.datn.financeapp.group.helper.BalanceCalculator;
import com.datn.financeapp.group.helper.MemberBalances;
import com.datn.financeapp.group.repository.GroupTransactionRepository;
import com.datn.financeapp.group.repository.MemberRepository;
import com.datn.financeapp.group.service.GTransactionBuilderStrategy;
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
 * <li>{@link #supports(TransactionType)}: Xác nhận hỗ trợ loại REFUND.</li>
 * <li>{@link #build(UUID, UUID, GroupTransactionCreateReq, TransactionStatus, boolean)}:
 * Kiểm tra nguồn tiền FUND, tính số dư còn lại của người nhận để đối soát hạn mức
 * hoàn trả và hoàn thiện thực thể giao dịch.</li>
 * </ul>
 * </p>
 */
@Component
@RequiredArgsConstructor
public class RefundTransactionStrategy implements GTransactionBuilderStrategy {

    private final GroupTransactionRepository transactionRepository;
    private final MemberRepository memberRepository;

    @Override
    public boolean supports(TransactionType type) {
        return type == TransactionType.REFUND;
    }

    @Override
    public TransactionStatus determineStatus(com.datn.financeapp.group.helper.MemberAuthInfo authInfo) {
        if (authInfo.isOwner() || authInfo.isTreasurer()) {
            return TransactionStatus.CONFIRMED;
        }
        throw new com.datn.financeapp.common.exception.BusinessException(com.datn.financeapp.common.exception.ErrorCode.TRANSACTION_TYPE_NOT_ALLOWED);
    }

    @Override
    public GTransaction build(UUID operatorId, UUID groupId, GroupTransactionCreateReq req,
                              TransactionStatus status, boolean isSettlementEnabled) {
        MoneySource source = req.resolveMoneySource();
        if (source != MoneySource.FUND) {
            throw new BusinessException(ErrorCode.MONEY_SOURCE_INVALID);
        }

        // tính toán số dư hiện tại để kiểm tra hạn mức hoàn trả
        List<GTransaction> allTxns = transactionRepository
                .findByGroupIdAndDeletedAtIsNullOrderByOccurredAtDescCreatedAtDesc(groupId);
        List<Member> allMembers = memberRepository
                .findByGroupIdAndStatusNotOrderByJoinedAtDesc(groupId, MemberStatus.PENDING);
        MemberBalances mb = BalanceCalculator.calculateBalances(allTxns, allMembers, null);

        GroupTransactionTypeValidator.validRefundLimit(req.transactorId(), req.amount(), mb);

        return baseBuilder(operatorId, groupId, req, source, status)
                .categoryId(null)
                .participants(List.of())
                .build();
    }
}

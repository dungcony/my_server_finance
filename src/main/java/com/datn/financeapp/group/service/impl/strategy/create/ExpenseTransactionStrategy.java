package com.datn.financeapp.group.service.impl.strategy.create;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionCreateReq;
import com.datn.financeapp.group.entity.GTransaction;
import com.datn.financeapp.group.entity.TransactionParticipant;
import com.datn.financeapp.group.enums.TransactionStatus;
import com.datn.financeapp.group.enums.TransactionType;
import com.datn.financeapp.group.events.ValidCategorySystemEvent;
import com.datn.financeapp.group.helper.TransactionHelper;
import com.datn.financeapp.group.service.GTransactionBuilderStrategy;
import com.datn.financeapp.group.service.MemberService;
import com.datn.financeapp.group.validator.GroupTransactionPaticipantValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Chiến lược xây dựng giao dịch chi tiêu nhóm (EXPENSE).
 * <p>
 * Chi tiết các phương thức:
 * <ul>
 * <li>{@link #supports(TransactionType)}: Xác nhận hỗ trợ loại EXPENSE.</li>
 * <li>{@link #build(UUID, UUID, GroupTransactionCreateReq, TransactionStatus, boolean)}:
 * Kiểm tra danh mục, tính toán phân bổ chi phí cho người tham gia và hoàn thiện
 * thực thể giao dịch.</li>
 * </ul>
 * </p>
 */
@Component
@RequiredArgsConstructor
public class ExpenseTransactionStrategy implements GTransactionBuilderStrategy {

    private final GroupTransactionPaticipantValidator transactionValidator;
    private final TransactionHelper transactionHelper;
    private final MemberService memberService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public boolean supports(TransactionType type) {
        return type == TransactionType.EXPENSE;
    }

    @Override
    public TransactionStatus determineStatus(com.datn.financeapp.group.helper.MemberAuthInfo authInfo) {
        if (authInfo.isOwner() || authInfo.isTreasurer()) {
            return TransactionStatus.CONFIRMED;
        }
        return TransactionStatus.PENDING;
    }

    @Override
    public GTransaction build(UUID operatorId, UUID groupId, GroupTransactionCreateReq req,
                              TransactionStatus status, boolean isSettlementEnabled) {
        // chi tiêu nhóm bắt buộc có danh mục
        if (req.categoryId() == null) {
            throw new BusinessException(ErrorCode.CATEGORY_REQUIRED_FOR_EXPENSE);
        }
        eventPublisher.publishEvent(new ValidCategorySystemEvent(req.categoryId()));

        // kiểm tra người chi và danh sách chia tiền
        transactionValidator.validTransactorAndParticipants(groupId, req.transactorId(), req.participants(),
                req.amount());

        // phân bổ số tiền cho từng người tham gia
        List<TransactionParticipant> participants = transactionHelper.resolveParticipants(
                req.amount(),
                req.participants(),
                () -> memberService.findIdAllMember(groupId));

        return baseBuilder(operatorId, groupId, req, req.resolveMoneySource(), status)
                .categoryId(req.categoryId())
                .participants(participants)
                .build();
    }
}

package com.datn.financeapp.module.group.service.impl.strategy.create;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.category.service.CategoryService;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTransactionCreateReq;
import com.datn.financeapp.module.group.entity.GTransaction;
import com.datn.financeapp.module.group.entity.TransactionParticipant;
import com.datn.financeapp.module.group.enums.GTransactionStatus;
import com.datn.financeapp.module.group.enums.GTransactionType;
import com.datn.financeapp.module.group.helper.MemberAuthInfo;
import com.datn.financeapp.module.group.helper.TransactionHelper;
import com.datn.financeapp.module.group.helper.GTransactionBuilder;
import com.datn.financeapp.module.group.service.MemberService;
import com.datn.financeapp.module.group.validator.GroupTransactionPaticipantValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Chiến lược xây dựng giao dịch chi tiêu nhóm (EXPENSE).
 * <p>
 * Chi tiết các phương thức:
 * <ul>
 * <li>{@link #supports(GTransactionType)}: Xác nhận hỗ trợ loại EXPENSE.</li>
 * <li>{@link #build(UUID, UUID, GroupTransactionCreateReq, GTransactionStatus, boolean)}:
 * Kiểm tra danh mục, tính toán phân bổ chi phí cho người tham gia và hoàn thiện
 * thực thể giao dịch.</li>
 * </ul>
 * </p>
 */
@Component
@RequiredArgsConstructor
public class ExpenseTransaction implements GTransactionBuilder {

    private final GroupTransactionPaticipantValidator transactionValidator;
    private final TransactionHelper transactionHelper;
    private final MemberService memberService;
    private final CategoryService categoryService;

    @Override
    public boolean supports(GTransactionType type) {
        return type == GTransactionType.EXPENSE;
    }

    @Override
    public GTransactionStatus determineStatus(MemberAuthInfo authInfo) {
        if (authInfo.isOwner() || authInfo.isTreasurer()) {
            return GTransactionStatus.CONFIRMED;
        }
        return GTransactionStatus.PENDING;
    }

    @Override
    public GTransaction build(UUID operatorId, UUID groupId, GroupTransactionCreateReq req,
                              GTransactionStatus status, boolean isSettlementEnabled) {
        // chi tiêu nhóm bắt buộc có danh mục
        if (req.categoryCode() == null || req.categoryCode().isBlank()) {
            throw new BusinessException(ErrorCode.GROUP_TXN_CATEGORY_REQUIRED);
        }
        // tra id danh mục theo code, danh mục không tồn tại thì ném CATEGORY_NOT_FOUND
        UUID categoryId = categoryService.findIdByCode(req.categoryCode());

        // kiểm tra người chi và danh sách chia tiền
        transactionValidator.validTransactorAndParticipants(groupId, req.transactorId(), req.participants(),
                req.amount());

        // phân bổ số tiền cho từng người tham gia
        List<TransactionParticipant> participants = transactionHelper.resolveParticipants(
                req.amount(),
                req.participants(),
                () -> memberService.findIdAllMember(groupId));

        return baseBuilder(operatorId, groupId, req, req.resolveMoneySource(), status)
                .categoryId(categoryId)
                .participants(participants)
                .build();
    }
}

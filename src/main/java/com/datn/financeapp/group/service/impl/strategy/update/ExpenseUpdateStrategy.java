package com.datn.financeapp.group.service.impl.strategy.update;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionUpdateReq;
import com.datn.financeapp.group.entity.GTransaction;
import com.datn.financeapp.group.entity.TransactionParticipant;
import com.datn.financeapp.group.enums.GTransactionType;
import com.datn.financeapp.group.events.ValidCategorySystemEvent;
import com.datn.financeapp.group.helper.MemberAuthInfo;
import com.datn.financeapp.group.helper.TransactionHelper;
import com.datn.financeapp.group.service.GTransactionUpdateStrategy;
import com.datn.financeapp.group.service.MemberService;
import com.datn.financeapp.group.validator.GroupTransactionPaticipantValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Chiến lược cập nhật giao dịch chi tiêu nhóm (EXPENSE).
 * <p>
 * Chi tiết các phương thức:
 * <ul>
 * <li>{@link #supports(GTransactionType)}: Kiểm tra loại EXPENSE.</li>
 * <li>{@link #update(GTransaction, UUID, UUID, GroupTransactionUpdateReq, MemberAuthInfo)}:
 * Cập nhật danh mục, người chi tiền và danh sách người tham gia chia tiền.</li>
 * </ul>
 * </p>
 */
@Component
@RequiredArgsConstructor
public class ExpenseUpdateStrategy implements GTransactionUpdateStrategy {

    private final GroupTransactionPaticipantValidator transactionValidator;
    private final TransactionHelper transactionHelper;
    private final MemberService memberService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public boolean supports(GTransactionType type) {
        return type == GTransactionType.EXPENSE;
    }

    @Override
    public void update(GTransaction txn, UUID operatorId, UUID groupId, GroupTransactionUpdateReq req,
                       MemberAuthInfo authInfo) {
        // kiểm tra bắt buộc truyền lại người chia tiền khi thay đổi tổng số tiền
        if (req.amount() != null && !req.amount().equals(txn.getAmount())) {
            if (req.participants() == null || req.participants().isEmpty()) {
                throw new BusinessException(ErrorCode.PARTICIPANTS_REQUIRED_ON_AMOUNT_CHANGE);
            }
        }

        // cập nhật các trường chung và trạng thái kiểm duyệt
        applyBaseUpdate(txn, operatorId, req, authInfo);

        // cập nhật danh mục nếu có truyền lên
        UUID categoryId = req.categoryId() != null ? req.categoryId() : txn.getCategoryId();
        if (categoryId == null) {
            throw new BusinessException(ErrorCode.CATEGORY_REQUIRED_FOR_EXPENSE);
        }
        if (req.categoryId() != null) {
            eventPublisher.publishEvent(new ValidCategorySystemEvent(categoryId));
        }

        // cập nhật người chi tiền nếu có truyền
        UUID transactorId = req.transactorId() != null ? req.transactorId() : txn.getTransactorId();

        // cập nhật danh sách người chia tiền nếu có truyền
        if (req.participants() != null) {
            transactionValidator.validTransactorAndParticipants(groupId, transactorId, req.participants(),
                    txn.getAmount());
            List<TransactionParticipant> participants = transactionHelper.resolveParticipants(
                    txn.getAmount(),
                    req.participants(),
                    () -> memberService.findIdAllMember(groupId));
            txn.getParticipants().clear();
            txn.getParticipants().addAll(participants);
        }

        txn.setTransactorId(transactorId);
        txn.setCategoryId(categoryId);
        if (req.moneySource() != null) {
            txn.setMoneySource(req.moneySource());
        }
    }
}

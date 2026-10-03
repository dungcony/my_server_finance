package com.datn.financeapp.group.service.impl.strategy.create;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionCreateReq;
import com.datn.financeapp.group.entity.GTransaction;
import com.datn.financeapp.group.entity.TransactionParticipant;
import com.datn.financeapp.group.enums.GTransactionType;
import com.datn.financeapp.group.enums.MoneySource;
import com.datn.financeapp.group.enums.GTransactionStatus;
import com.datn.financeapp.group.helper.TransactionHelper;
import com.datn.financeapp.group.helper.GTransactionBuilder;
import com.datn.financeapp.group.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Chiến lược xây dựng giao dịch điều chỉnh số dư quỹ sau kiểm kê (ADJUSTMENT_UP
 * &amp; ADJUSTMENT_DOWN).
 * <p>
 * Chi tiết các phương thức:
 * <ul>
 * <li>{@link #supports(GTransactionType)}: Xác nhận hỗ trợ loại ADJUSTMENT_UP
 * hoặc ADJUSTMENT_DOWN.</li>
 * <li>{@link #build(UUID, UUID, GroupTransactionCreateReq, GTransactionStatus, boolean)}:
 * Kiểm tra nguồn tiền FUND, tự động phân bổ chia đều khoản chênh lệch cho thành
 * viên nhóm và hoàn thiện thực thể giao dịch.</li>
 * </ul>
 * </p>
 */
@Component
@RequiredArgsConstructor
public class AdjustmentTransaction implements GTransactionBuilder {

    private final MemberService memberService;
    private final TransactionHelper transactionHelper;

    @Override
    public boolean supports(GTransactionType type) {
        return type == GTransactionType.ADJUSTMENT_UP || type == GTransactionType.ADJUSTMENT_DOWN;
    }

    @Override
    public GTransactionStatus determineStatus(com.datn.financeapp.group.helper.MemberAuthInfo authInfo) {
        if (authInfo.isOwner()) {
            return GTransactionStatus.CONFIRMED;
        }
        return GTransactionStatus.PENDING;
    }

    @Override
    public GTransaction build(UUID operatorId, UUID groupId, GroupTransactionCreateReq req,
                              GTransactionStatus status, boolean isSettlementEnabled) {
        MoneySource source = req.resolveMoneySource();
        if (source != MoneySource.FUND) {
            throw new BusinessException(ErrorCode.GROUP_TXN_MONEY_SOURCE_INVALID);
        }

        // tự động chia đều cho toàn bộ thành viên trong nhóm nếu client không truyền
        // danh sách
        List<TransactionParticipant> participants;
        if (req.participants() == null || req.participants().isEmpty()) {
            participants = memberService.findIdAllMember(groupId).stream()
                    .map(uid -> TransactionParticipant.builder()
                            .userId(uid)
                            .shareAmount(null)
                            .build())
                    .toList();
        } else {
            participants = transactionHelper.resolveParticipants(
                    req.amount(),
                    req.participants(),
                    () -> memberService.findIdAllMember(groupId));
        }

        return baseBuilder(operatorId, groupId, req, source, status)
                .categoryId(null)
                .participants(participants)
                .build();
    }
}

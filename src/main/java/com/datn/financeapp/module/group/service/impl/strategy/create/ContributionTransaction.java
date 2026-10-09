package com.datn.financeapp.module.group.service.impl.strategy.create;

import com.datn.financeapp.module.group.dto.request.transaction.GroupTransactionCreateReq;
import com.datn.financeapp.module.group.entity.GTransaction;
import com.datn.financeapp.module.group.enums.GTransactionStatus;
import com.datn.financeapp.module.group.enums.GTransactionType;
import com.datn.financeapp.module.group.enums.MoneySource;
import com.datn.financeapp.module.group.helper.GTransactionBuilder;
import com.datn.financeapp.module.group.helper.MemberAuthInfo;
import com.datn.financeapp.module.group.validator.GroupTransactionTypeValidator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Chiến lược xây dựng giao dịch nộp quỹ nhóm (CONTRIBUTION).
 * <p>
 * Chi tiết các phương thức:
 * <ul>
 * <li>{@link #supports(GTransactionType)}: Xác nhận hỗ trợ loại
 * CONTRIBUTION.</li>
 * <li>{@link #build(UUID, UUID, GroupTransactionCreateReq, GTransactionStatus, boolean)}:
 * Kiểm tra nguồn tiền PERSONAL, xác nhận không có người chia tiền và hoàn thiện
 * thực thể giao dịch.</li>
 * </ul>
 * </p>
 */
@Component
public class ContributionTransaction implements GTransactionBuilder {

    @Override
    public boolean supports(GTransactionType type) {
        return type == GTransactionType.CONTRIBUTION;
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
        MoneySource source = req.resolveMoneySource();
        // kiểm tra nguồn tiền cá nhân và đảm bảo không có người chia tiền
        GroupTransactionTypeValidator.validContribution(source, req.participants());

        return baseBuilder(operatorId, groupId, req, source, status)
                .categoryId(null)
                .participants(List.of())
                .build();
    }
}

package com.datn.financeapp.module.group.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.group.entity.GroupTxn;
import com.datn.financeapp.module.group.enums.GroupTxnStatus;
import com.datn.financeapp.module.group.events.FundBalanceChangedEvent;
import com.datn.financeapp.module.group.helper.*;
import com.datn.financeapp.module.group.repository.GroupTxnRepository;
import com.datn.financeapp.module.group.service.GroupTxnDeleteService;
import com.datn.financeapp.module.group.service.MemberBalanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class GroupTxnDeleteImpl implements GroupTxnDeleteService {

    private final GroupTxnRepository groupTxnRepository;

    private final MemberAuthCache memberAuthCache;
    private final ApplicationEventPublisher applicationEventPublisher;

    private final MemberBalanceService balanceService;

    @Override
    public void softDelete(UUID groupId, UUID txnId) {

        UUID operatorId = SecurityContextUtil.currentUserId();
        GroupTxn txn = groupTxnRepository.findByIdAndGroupIdAndDeletedAtIsNull(txnId, groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_TRANSACTION_NOT_FOUND));

        memberAuthCache.get(groupId, operatorId)
                .filter(MemberAuthInfo::inGroupNotDeleted)
                .filter(m -> !m.isMember())
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));

        // hoàn tác số dư quỹ và thành viên nếu giao dịch đã được xác nhận
        if (txn.getStatus() == GroupTxnStatus.CONFIRMED) {
            MemberBalances before = BalanceCalculator.effectOf(txn);
            long delta = GroupTxnHelper.calculateDelta(txn.getType(), txn.getMoneySource(), txn.getAmount(), true);
            applicationEventPublisher.publishEvent(new FundBalanceChangedEvent(groupId, delta));
            balanceService.applyDelta(groupId, before, new MemberBalances());
        }

        // đánh dấu xóa mềm
        txn.setDeletedAt(Instant.now());

        groupTxnRepository.save(txn);
    }
}

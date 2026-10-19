package com.datn.financeapp.module.group.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.category.service.CategoryService;
import com.datn.financeapp.module.group.dto.request.transaction.*;
import com.datn.financeapp.module.group.dto.response.transaction.GroupTxnDetailRes;
import com.datn.financeapp.module.group.entity.GroupTxn;
import com.datn.financeapp.module.group.entity.GroupTxnParticipant;
import com.datn.financeapp.module.group.enums.GroupTxnStatus;
import com.datn.financeapp.module.group.enums.GroupTxnType;
import com.datn.financeapp.module.group.enums.MoneySource;
import com.datn.financeapp.module.group.events.FundBalanceChangedEvent;
import com.datn.financeapp.module.group.events.GroupTxnConfirmedEvent;
import com.datn.financeapp.module.group.helper.*;
import com.datn.financeapp.module.group.mapper.GroupTxnMapper;
import com.datn.financeapp.module.group.repository.GroupTxnRepository;
import com.datn.financeapp.module.group.service.GroupTxnUpdateService;
import com.datn.financeapp.module.group.service.MemberBalanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class GroupTxnUpdateImpl implements GroupTxnUpdateService {

    private final GroupTxnRepository groupTxnRepository;
    private final GroupTxnMapper groupTransactionMapper;
    private final GroupTxnHelper groupTxnHelper;

    private final MemberAuthCache memberAuthCache;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final CategoryService categoryService;

    private final MemberBalanceService balanceService;

    @Override
    public GroupTxnDetailRes update(UUID groupId, GroupTxnUpdateReq req) {
        UUID operatorId = SecurityContextUtil.currentUserId();
        GroupTxn txn = groupTxnRepository.findByIdAndGroupIdAndDeletedAtIsNull(req.txnId(), groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_TRANSACTION_NOT_FOUND));

        if (txn.getStatus() == GroupTxnStatus.REJECTED)
            throw new BusinessException(ErrorCode.GROUP_TXN_IS_REJECT);

        MemberAuthInfo auth = memberAuthCache.get(groupId, operatorId)
                .filter(MemberAuthInfo::inGroupActive)
                .filter(m -> m.isEditGroupTransaction(txn))
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));

        MemberBalances before = BalanceCalculator.effectOf(txn);
        long oldDelta = GroupTxnHelper.calculateDelta(txn);

        if (req.type() != null || req.moneySource() != null || req.transactorId() != null || req.categoryCode() != null || req.participants() != null)
            validTypeAndMoneySources(txn, req.type(), req.moneySource(), req.transactorId(), req.categoryCode(), req.participants(), auth, operatorId);

        if (req.amount() != null) {
            if (txn.getType() != GroupTxnType.CONTRIBUTION && req.participants() == null)
                throw new BusinessException(ErrorCode.GROUP_TXN_PARTICIPANTS_REQUIRED);
            txn.setAmount(req.amount());
        }

        if (req.transactorId() != null)
            txn.setTransactorId(req.transactorId());

        if (req.participants() != null) {
            groupTxnHelper.validParticipants(groupId, txn.getTransactorId(), req.participants(), txn.getAmount());
            txn.setParticipants(GroupTxnHelper.getParticipants(req.participants()));
        }

        if (txn.getType() == GroupTxnType.CONTRIBUTION || txn.getType() == GroupTxnType.REFUND) {
            txn.setCategoryCode(null);
        } else if (req.categoryCode() != null) {
            categoryService.findByCodeAndIsDeletedAndIsLeaf(req.categoryCode(), false, true)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Danh mục không tồn tại hoặc không phải danh mục con."));
            txn.setCategoryCode(req.categoryCode());
        }

        if (req.note() != null)
            txn.setNote(req.note());

        if (req.occurredAt() != null)
            txn.setOccurredAt(req.occurredAt());

        if (txn.getStatus() == GroupTxnStatus.CONFIRMED) {
            balanceService.applyDelta(txn.getGroupId(), before, BalanceCalculator.effectOf(txn));
            long fundDelta = GroupTxnHelper.calculateDelta(txn) - oldDelta;
            if (fundDelta != 0)
                applicationEventPublisher.publishEvent(new FundBalanceChangedEvent(txn.getGroupId(), fundDelta));
        }

        return groupTransactionMapper.toDetailResponse(txn);
    }

    @Override
    public GroupTxnDetailRes status(UUID groupId, GroupTxnUpdateStatusReq req) {
        if (req.status() != GroupTxnStatus.CONFIRMED && req.status() != GroupTxnStatus.REJECTED) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        UUID operatorId = SecurityContextUtil.currentUserId();
        Instant now = Instant.now();

        MemberAuthInfo auth = memberAuthCache.get(groupId, operatorId)
                .filter(MemberAuthInfo::inGroupActive)
                .filter(a -> !a.isMember())
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));

        GroupTxn transaction = groupTxnRepository.findByIdAndGroupIdAndDeletedAtIsNull(req.txnId(), groupId)
                .filter(t -> t.getStatus() == GroupTxnStatus.PENDING)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_TXN_NOT_FOUD));

        if ((transaction.getType() == GroupTxnType.ADJUSTMENT_UP || transaction.getType() == GroupTxnType.ADJUSTMENT_DOWN)
                && !auth.isOwner()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        transaction.setReviewedBy(operatorId);
        transaction.setReviewedAt(now);
        transaction.setStatus(req.status());

        if (req.status() == GroupTxnStatus.CONFIRMED) {
            balanceService.applyDelta(groupId, new MemberBalances(), BalanceCalculator.effectOf(transaction));
            applicationEventPublisher.publishEvent(new FundBalanceChangedEvent(groupId, GroupTxnHelper.calculateDelta(transaction)));

            if (transaction.getType() != GroupTxnType.ADJUSTMENT_UP && transaction.getType() != GroupTxnType.ADJUSTMENT_DOWN) {
                applicationEventPublisher.publishEvent(GroupTxnConfirmedEvent.of(transaction));
            }
        }

        return groupTransactionMapper.toDetailResponse(transaction);
    }

    @Override
    public int statuses(UUID groupId, BatchGroupTxnUpdateStatusReq req) {
        if (req.status() != GroupTxnStatus.CONFIRMED && req.status() != GroupTxnStatus.REJECTED) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        UUID operatorId = SecurityContextUtil.currentUserId();
        Instant now = Instant.now();
        MemberAuthInfo auth = memberAuthCache.get(groupId, operatorId)
                .filter(MemberAuthInfo::inGroupActive)
                .filter(a -> !a.isMember())
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));

        List<GroupTxn> txns = groupTxnRepository.findByIdInAndGroupIdAndDeletedAtIsNullAndStatus(req.txnIds(), groupId, GroupTxnStatus.PENDING);

        if (txns.size() != req.txnIds().size())
            throw new BusinessException(ErrorCode.GROUP_TXN_NOT_FOUD);

        boolean hasAdjustment = txns.stream().anyMatch(t -> t.getType() == GroupTxnType.ADJUSTMENT_UP || t.getType() == GroupTxnType.ADJUSTMENT_DOWN);
        if (hasAdjustment && !auth.isOwner()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (req.status() == GroupTxnStatus.CONFIRMED) {
            long totalDelta = 0L;
            MemberBalances totalEffect = new MemberBalances();

            for (var t : txns) {
                t.setReviewedAt(now);
                t.setStatus(GroupTxnStatus.CONFIRMED);
                t.setReviewedBy(operatorId);

                totalDelta += GroupTxnHelper.calculateDelta(t);
                totalEffect = totalEffect.plus(BalanceCalculator.effectOf(t));

                if (t.getType() != GroupTxnType.ADJUSTMENT_UP && t.getType() != GroupTxnType.ADJUSTMENT_DOWN) {
                    applicationEventPublisher.publishEvent(GroupTxnConfirmedEvent.of(t));
                }
            }

            balanceService.applyDelta(groupId, new MemberBalances(), totalEffect);
            applicationEventPublisher.publishEvent(new FundBalanceChangedEvent(groupId, totalDelta));
        } else {
            for (var t : txns) {
                t.setReviewedAt(now);
                t.setStatus(GroupTxnStatus.REJECTED);
                t.setReviewedBy(operatorId);
            }
        }

        return txns.size();
    }

    //-------------------------------------- PRIVATE -----------------------------------------//

    private void validTypeAndMoneySources(GroupTxn txn,
                                          GroupTxnType newType, MoneySource newSource, UUID newTransactorId, String newCateCode, List<GroupTxnParticipantReq> newParticipants,
                                          MemberAuthInfo auth, UUID operatorId) {

        GroupTxnType type = newType != null ? newType : txn.getType();
        MoneySource source = newSource != null ? newSource : txn.getMoneySource();

        UUID transactorId = newTransactorId != null ? newTransactorId
                : (source == MoneySource.FUND || type == GroupTxnType.REFUND) ? null : txn.getTransactorId();

        List<GroupTxnParticipant> participants = newParticipants != null
                ? GroupTxnHelper.getParticipants(newParticipants) : txn.getParticipants();

        boolean hasCategory = (newCateCode != null && !newCateCode.isBlank())
                || (newType == null && txn.getCategoryCode() != null);

        switch (type) {
            case CONTRIBUTION -> {
                if (source == MoneySource.FUND)
                    throw new BusinessException(ErrorCode.GROUP_TXN_MONEY_SOURCE_INVALID);
                if (transactorId == null)
                    throw new BusinessException(ErrorCode.GROUP_TXN_TRANSACTOR_REQUIRED);
                if (participants.size() > 1)
                    throw new BusinessException(ErrorCode.GROUP_TXN_PARTICIPANTS_NOT_ALLOWED);
            }
            case EXPENSE -> {
                if (source == MoneySource.PERSONAL && transactorId == null)
                    throw new BusinessException(ErrorCode.GROUP_TXN_TRANSACTOR_REQUIRED);
                if (source == MoneySource.FUND && transactorId != null)
                    throw new BusinessException(ErrorCode.GROUP_TXN_TRANSACTOR_REQUIRED_NULL);
                if (participants.isEmpty())
                    throw new BusinessException(ErrorCode.GROUP_TXN_PARTICIPANT_REQUIRED);
                if (!hasCategory)
                    throw new BusinessException(ErrorCode.GROUP_TXN_CATEGORY_REQUIRED);
            }
            case REFUND -> {
                if (!auth.isTreasurer())
                    throw new BusinessException(ErrorCode.FORBIDDEN);
                if (source == MoneySource.PERSONAL)
                    throw new BusinessException(ErrorCode.GROUP_TXN_MONEY_SOURCE_INVALID);
                if (transactorId != null)
                    throw new BusinessException(ErrorCode.GROUP_TXN_TRANSACTOR_REQUIRED_NULL);
                if (participants.isEmpty())
                    throw new BusinessException(ErrorCode.GROUP_TXN_PARTICIPANT_REQUIRED);
            }
            default -> throw new BusinessException(ErrorCode.GROUP_TXN_ADJUSTMENT_NOT_EDITABLE);
        }

        if (auth.isMember() && transactorId != null && !operatorId.equals(transactorId))
            throw new BusinessException(ErrorCode.GROUP_TXN_EDIT_FORBIDDEN);

        txn.setType(type);
        txn.setMoneySource(source);
        txn.setTransactorId(transactorId);
    }

}

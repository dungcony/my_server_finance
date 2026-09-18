package com.datn.financeapp.group.service.impl;

import com.datn.financeapp.category.entity.Category;
import com.datn.financeapp.category.repository.CategoryRepository;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.transaction.GroupRefundReq;
import com.datn.financeapp.group.dto.transaction.GroupTransactionBulkReviewReq;
import com.datn.financeapp.group.dto.transaction.GroupTransactionBulkReviewRes;
import com.datn.financeapp.group.dto.transaction.GroupTransactionCreateReq;
import com.datn.financeapp.group.dto.transaction.GroupTransactionDetailRes;
import com.datn.financeapp.group.dto.transaction.GroupTransactionFilterReq;
import com.datn.financeapp.group.dto.transaction.GroupTransactionParticipantReq;
import com.datn.financeapp.group.dto.transaction.GroupTransactionParticipantRes;
import com.datn.financeapp.group.dto.transaction.GroupTransactionUpdateReq;
import com.datn.financeapp.group.dto.transaction.GroupWithdrawalReq;
import com.datn.financeapp.group.entity.Group;
import com.datn.financeapp.group.entity.GroupMember;
import com.datn.financeapp.group.entity.GroupTransaction;
import com.datn.financeapp.group.entity.GroupTransactionParticipant;
import com.datn.financeapp.group.entity.GroupWallet;
import com.datn.financeapp.group.enums.GroupRole;
import com.datn.financeapp.group.enums.GroupStatus;
import com.datn.financeapp.group.enums.GroupTransactionStatus;
import com.datn.financeapp.group.enums.GroupTransactionType;
import com.datn.financeapp.group.enums.GroupWalletStatus;
import com.datn.financeapp.group.enums.MemberStatus;
import com.datn.financeapp.group.enums.MoneySource;
import com.datn.financeapp.group.repository.GroupMemberRepository;
import com.datn.financeapp.group.repository.GroupRepository;
import com.datn.financeapp.group.repository.GroupTransactionParticipantRepository;
import com.datn.financeapp.group.repository.GroupTransactionRepository;
import com.datn.financeapp.group.repository.GroupWalletRepository;
import com.datn.financeapp.group.service.GroupBalanceCalculator;
import com.datn.financeapp.group.service.GroupTransactionService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupTransactionServiceImpl implements GroupTransactionService {

    private final GroupTransactionRepository transactionRepository;
    private final GroupTransactionParticipantRepository participantRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupWalletRepository groupWalletRepository;
    private final CategoryRepository categoryRepository;
    private final GroupBalanceCalculator balanceCalculator;

    @Override
    @Transactional
    public GroupTransactionDetailRes create(UUID userId, UUID groupId, GroupTransactionCreateReq req) {
        verifyActiveMember(groupId, userId);
        Group group = findActiveGroup(groupId);
        GroupWallet wallet = getActiveWallet(groupId);

        if (req.type() != GroupTransactionType.EXPENSE && req.type() != GroupTransactionType.CONTRIBUTION) {
            throw new BusinessException(ErrorCode.TRANSACTION_TYPE_NOT_ALLOWED);
        }

        Instant occurredAt = req.resolveOccurredAt();
        if (occurredAt.isAfter(Instant.now())) {
            throw new BusinessException(ErrorCode.DATE_IN_FUTURE);
        }

        MoneySource moneySource = req.moneySource();
        if (req.type() == GroupTransactionType.CONTRIBUTION) {
            if (moneySource != MoneySource.PERSONAL) {
                throw new BusinessException(ErrorCode.MONEY_SOURCE_INVALID);
            }
            if (req.participants() != null && !req.participants().isEmpty()) {
                throw new BusinessException(ErrorCode.PARTICIPANTS_NOT_ALLOWED);
            }
        }

        UUID categoryId = null;
        if (req.type() == GroupTransactionType.EXPENSE) {
            if (req.categoryId() == null) {
                throw new BusinessException(ErrorCode.CATEGORY_REQUIRED_FOR_EXPENSE);
            }
            Category category = categoryRepository.findById(req.categoryId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
            if (category.getUserId() != null || !"expense".equalsIgnoreCase(category.getType())) {
                throw new BusinessException(ErrorCode.SYSTEM_CATEGORY_REQUIRED);
            }
            categoryId = category.getId();
        }

        UUID targetUserId = req.userId() != null ? req.userId() : userId;
        List<UUID> membersAtOccurredAt = groupMemberRepository.findMemberUserIdsAtOccurredAt(groupId, occurredAt);

        if (req.type() == GroupTransactionType.EXPENSE) {
            if (!membersAtOccurredAt.contains(targetUserId)) {
                throw new BusinessException(ErrorCode.PAYER_NOT_MEMBER);
            }
        } else {
            // CONTRIBUTION: cho phép cựu thành viên nộp bù nếu đang có phần âm
            if (!membersAtOccurredAt.contains(targetUserId)) {
                boolean wasMember = groupMemberRepository.findByGroupIdAndUserIdAndStatusIn(
                        groupId, targetUserId, List.of(MemberStatus.LEFT, MemberStatus.REMOVED)
                ).isPresent();
                long netBalance = balanceCalculator.getNetBalance(groupId, targetUserId, null);
                if (!wasMember || netBalance >= 0) {
                    throw new BusinessException(ErrorCode.PAYER_NOT_MEMBER);
                }
            }
        }

        // Validate participants cho EXPENSE
        List<GroupTransactionParticipantReq> pList = req.participants();
        boolean saveParticipants = false;
        if (req.type() == GroupTransactionType.EXPENSE && pList != null && !pList.isEmpty()) {
            Set<UUID> seenIds = new HashSet<>();
            for (GroupTransactionParticipantReq p : pList) {
                if (!seenIds.add(p.userId())) {
                    throw new BusinessException(ErrorCode.PARTICIPANT_NOT_MEMBER);
                }
                if (!membersAtOccurredAt.contains(p.userId())) {
                    throw new BusinessException(ErrorCode.PARTICIPANT_NOT_MEMBER);
                }
            }

            boolean anyNull = pList.stream().anyMatch(p -> p.shareAmount() == null);
            boolean anyNonNull = pList.stream().anyMatch(p -> p.shareAmount() != null);
            if (anyNull && anyNonNull) {
                throw new BusinessException(ErrorCode.PARTICIPANTS_SHARE_MIXED);
            }

            if (anyNonNull) {
                long sum = pList.stream().mapToLong(GroupTransactionParticipantReq::shareAmount).sum();
                boolean anyNonPositive = pList.stream().anyMatch(p -> p.shareAmount() <= 0);
                if (sum != req.amount() || anyNonPositive) {
                    throw new BusinessException(ErrorCode.PARTICIPANTS_SUM_MISMATCH);
                }
                saveParticipants = true;
            } else {
                // Tất cả đều null: nếu tập participants bằng đúng toàn bộ thành viên lúc đó thì không lưu dòng nào
                if (seenIds.size() != membersAtOccurredAt.size() || !seenIds.containsAll(membersAtOccurredAt)) {
                    saveParticipants = true;
                }
            }
        }

        // Xác định trạng thái duyệt
        boolean isOwnerOrTreasurer = isOwner(groupId, userId) || wallet.getHeldByUserId().equals(userId);
        GroupTransactionStatus status;
        UUID reviewedBy = null;
        Instant reviewedAt = null;

        if (isOwnerOrTreasurer) {
            status = GroupTransactionStatus.CONFIRMED;
            reviewedBy = userId;
            reviewedAt = Instant.now();
        } else {
            status = GroupTransactionStatus.PENDING;
        }

        Instant now = Instant.now();
        GroupTransaction txn = GroupTransaction.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .moneySource(moneySource)
                .userId(targetUserId)
                .createdBy(userId)
                .categoryId(categoryId)
                .type(req.type())
                .status(status)
                .reviewedBy(reviewedBy)
                .reviewedAt(reviewedAt)
                .amount(req.amount())
                .occurredAt(occurredAt)
                .note(req.note() != null ? req.note().trim() : null)
                .createdAt(now)
                .updatedAt(now)
                .build();

        txn = transactionRepository.save(txn);

        if (saveParticipants && pList != null) {
            List<GroupTransactionParticipant> participants = new ArrayList<>();
            for (GroupTransactionParticipantReq pr : pList) {
                participants.add(GroupTransactionParticipant.builder()
                        .groupTransactionId(txn.getId())
                        .userId(pr.userId())
                        .shareAmount(pr.shareAmount())
                        .build());
            }
            participantRepository.saveAll(participants);
        }

        // Cập nhật số dư quỹ nếu CONFIRMED
        if (status == GroupTransactionStatus.CONFIRMED) {
            applyBalanceChange(groupId, wallet.getId(), txn.getType(), txn.getMoneySource(), txn.getAmount(), false);
        }

        return buildDetailRes(txn);
    }

    @Override
    @Transactional
    public GroupTransactionDetailRes confirm(UUID userId, UUID groupId, UUID transactionId) {
        verifyOwnerOrTreasurer(groupId, userId);
        findActiveGroup(groupId);
        GroupWallet wallet = getActiveWallet(groupId);

        GroupTransaction txn = transactionRepository.findByIdAndGroupIdAndDeletedAtIsNull(transactionId, groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_TRANSACTION_NOT_FOUND));

        if (txn.getStatus() != GroupTransactionStatus.PENDING) {
            throw new BusinessException(ErrorCode.TRANSACTION_NOT_PENDING);
        }

        txn.setStatus(GroupTransactionStatus.CONFIRMED);
        txn.setReviewedBy(userId);
        txn.setReviewedAt(Instant.now());
        txn.setUpdatedAt(Instant.now());

        applyBalanceChange(groupId, wallet.getId(), txn.getType(), txn.getMoneySource(), txn.getAmount(), false);
        txn = transactionRepository.save(txn);

        return buildDetailRes(txn);
    }

    @Override
    @Transactional
    public GroupTransactionDetailRes reject(UUID userId, UUID groupId, UUID transactionId) {
        verifyOwnerOrTreasurer(groupId, userId);
        findActiveGroup(groupId);

        GroupTransaction txn = transactionRepository.findByIdAndGroupIdAndDeletedAtIsNull(transactionId, groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_TRANSACTION_NOT_FOUND));

        if (txn.getStatus() != GroupTransactionStatus.PENDING) {
            throw new BusinessException(ErrorCode.TRANSACTION_NOT_PENDING);
        }

        txn.setStatus(GroupTransactionStatus.REJECTED);
        txn.setReviewedBy(userId);
        txn.setReviewedAt(Instant.now());
        txn.setUpdatedAt(Instant.now());
        txn = transactionRepository.save(txn);

        return buildDetailRes(txn);
    }

    @Override
    @Transactional
    public GroupTransactionBulkReviewRes bulkConfirm(UUID userId, UUID groupId, GroupTransactionBulkReviewReq req) {
        verifyOwnerOrTreasurer(groupId, userId);
        findActiveGroup(groupId);
        GroupWallet wallet = getActiveWallet(groupId);

        List<UUID> ids = req.transactionIds();
        if (ids == null || ids.isEmpty()) {
            return new GroupTransactionBulkReviewRes(0, 0, 0, List.of());
        }

        List<GroupTransaction> txns = transactionRepository.findByIdInAndGroupIdAndDeletedAtIsNull(ids, groupId);
        if (txns.size() != ids.size() || txns.stream().anyMatch(t -> t.getStatus() != GroupTransactionStatus.PENDING)) {
            throw new BusinessException(ErrorCode.TRANSACTION_NOT_PENDING);
        }

        groupWalletRepository.findByGroupIdForUpdate(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_WALLET_NOT_FOUND));

        long totalDelta = 0L;
        Instant now = Instant.now();
        for (GroupTransaction t : txns) {
            t.setStatus(GroupTransactionStatus.CONFIRMED);
            t.setReviewedBy(userId);
            t.setReviewedAt(now);
            t.setUpdatedAt(now);

            totalDelta += calculateDelta(t.getType(), t.getMoneySource(), t.getAmount(), false);
        }

        if (totalDelta != 0L) {
            groupWalletRepository.adjustBalance(wallet.getId(), totalDelta);
        }
        transactionRepository.saveAll(txns);

        return new GroupTransactionBulkReviewRes(ids.size(), txns.size(), 0, ids);
    }

    @Override
    @Transactional
    public GroupTransactionBulkReviewRes bulkReject(UUID userId, UUID groupId, GroupTransactionBulkReviewReq req) {
        verifyOwnerOrTreasurer(groupId, userId);
        findActiveGroup(groupId);

        List<UUID> ids = req.transactionIds();
        if (ids == null || ids.isEmpty()) {
            return new GroupTransactionBulkReviewRes(0, 0, 0, List.of());
        }

        List<GroupTransaction> txns = transactionRepository.findByIdInAndGroupIdAndDeletedAtIsNull(ids, groupId);
        if (txns.size() != ids.size() || txns.stream().anyMatch(t -> t.getStatus() != GroupTransactionStatus.PENDING)) {
            throw new BusinessException(ErrorCode.TRANSACTION_NOT_PENDING);
        }

        Instant now = Instant.now();
        for (GroupTransaction t : txns) {
            t.setStatus(GroupTransactionStatus.REJECTED);
            t.setReviewedBy(userId);
            t.setReviewedAt(now);
            t.setUpdatedAt(now);
        }
        transactionRepository.saveAll(txns);

        return new GroupTransactionBulkReviewRes(ids.size(), txns.size(), 0, ids);
    }

    @Override
    @Transactional
    public GroupTransactionDetailRes createRefund(UUID userId, UUID groupId, GroupRefundReq req) {
        verifyOwnerOrTreasurer(groupId, userId);
        Group group = findActiveGroup(groupId);
        GroupWallet wallet = getActiveWallet(groupId);

        Instant occurredAt = req.resolveOccurredAt();
        if (occurredAt.isAfter(Instant.now())) {
            throw new BusinessException(ErrorCode.DATE_IN_FUTURE);
        }

        List<UUID> membersAtOccurredAt = groupMemberRepository.findMemberUserIdsAtOccurredAt(groupId, occurredAt);
        if (!membersAtOccurredAt.contains(req.toUserId())) {
            throw new BusinessException(ErrorCode.PAYER_NOT_MEMBER);
        }

        if (Boolean.TRUE.equals(group.getIsSettlementEnabled())) {
            long netBalance = balanceCalculator.getNetBalance(groupId, req.toUserId(), null);
            if (req.amount() > netBalance) {
                throw new BusinessException(ErrorCode.AMOUNT_EXCEEDS_SHARE);
            }
        }

        Instant now = Instant.now();
        GroupTransaction txn = GroupTransaction.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .moneySource(MoneySource.FUND)
                .userId(req.toUserId())
                .createdBy(userId)
                .categoryId(null)
                .type(GroupTransactionType.REFUND)
                .status(GroupTransactionStatus.CONFIRMED)
                .reviewedBy(userId)
                .reviewedAt(now)
                .amount(req.amount())
                .occurredAt(occurredAt)
                .note(req.note() != null ? req.note().trim() : null)
                .createdAt(now)
                .updatedAt(now)
                .build();

        txn = transactionRepository.save(txn);
        applyBalanceChange(groupId, wallet.getId(), txn.getType(), txn.getMoneySource(), txn.getAmount(), false);

        return buildDetailRes(txn);
    }

    @Override
    @Transactional
    public GroupTransactionDetailRes updateRefund(UUID userId, UUID groupId, UUID transactionId, GroupRefundReq req) {
        verifyOwnerOrTreasurer(groupId, userId);
        Group group = findActiveGroup(groupId);
        GroupWallet wallet = getActiveWallet(groupId);

        GroupTransaction txn = transactionRepository.findByIdAndGroupIdAndDeletedAtIsNull(transactionId, groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_TRANSACTION_NOT_FOUND));

        if (txn.getType() != GroupTransactionType.REFUND) {
            throw new BusinessException(ErrorCode.TRANSACTION_TYPE_NOT_ALLOWED);
        }

        Instant occurredAt = req.resolveOccurredAt();
        if (occurredAt.isAfter(Instant.now())) {
            throw new BusinessException(ErrorCode.DATE_IN_FUTURE);
        }

        List<UUID> membersAtOccurredAt = groupMemberRepository.findMemberUserIdsAtOccurredAt(groupId, occurredAt);
        if (!membersAtOccurredAt.contains(req.toUserId())) {
            throw new BusinessException(ErrorCode.PAYER_NOT_MEMBER);
        }

        if (Boolean.TRUE.equals(group.getIsSettlementEnabled())) {
            long netBalanceExcludingCurrent = balanceCalculator.getNetBalance(groupId, req.toUserId(), txn.getId());
            if (req.amount() > netBalanceExcludingCurrent) {
                throw new BusinessException(ErrorCode.AMOUNT_EXCEEDS_SHARE);
            }
        }

        groupWalletRepository.findByGroupIdForUpdate(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_WALLET_NOT_FOUND));
        long delta = txn.getAmount() - req.amount();
        if (delta != 0) {
            groupWalletRepository.adjustBalance(wallet.getId(), delta);
        }

        Instant now = Instant.now();
        txn.setUserId(req.toUserId());
        txn.setAmount(req.amount());
        txn.setOccurredAt(occurredAt);
        txn.setNote(req.note() != null ? req.note().trim() : null);
        txn.setReviewedBy(userId);
        txn.setReviewedAt(now);
        txn.setUpdatedAt(now);
        txn = transactionRepository.save(txn);

        return buildDetailRes(txn);
    }

    @Override
    @Transactional
    public GroupTransactionDetailRes createWithdrawal(UUID userId, UUID groupId, GroupWithdrawalReq req) {
        verifyOwnerOrTreasurer(groupId, userId);
        Group group = findActiveGroup(groupId);
        GroupWallet wallet = getActiveWallet(groupId);

        Instant occurredAt = req.resolveOccurredAt();
        if (occurredAt.isAfter(Instant.now())) {
            throw new BusinessException(ErrorCode.DATE_IN_FUTURE);
        }

        List<UUID> membersAtOccurredAt = groupMemberRepository.findMemberUserIdsAtOccurredAt(groupId, occurredAt);
        if (!membersAtOccurredAt.contains(req.fromUserId())) {
            throw new BusinessException(ErrorCode.PAYER_NOT_MEMBER);
        }

        long remainingContribution = balanceCalculator.getRemainingContribution(groupId, req.fromUserId(), null);
        if (req.amount() > remainingContribution) {
            throw new BusinessException(ErrorCode.AMOUNT_EXCEEDS_CONTRIBUTION);
        }

        if (Boolean.TRUE.equals(group.getIsSettlementEnabled())) {
            long netBalance = balanceCalculator.getNetBalance(groupId, req.fromUserId(), null);
            if (req.amount() > netBalance) {
                throw new BusinessException(ErrorCode.AMOUNT_EXCEEDS_SHARE);
            }
        }

        Instant now = Instant.now();
        GroupTransaction txn = GroupTransaction.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .moneySource(MoneySource.FUND)
                .userId(req.fromUserId())
                .createdBy(userId)
                .categoryId(null)
                .type(GroupTransactionType.WITHDRAWAL)
                .status(GroupTransactionStatus.CONFIRMED)
                .reviewedBy(userId)
                .reviewedAt(now)
                .amount(req.amount())
                .occurredAt(occurredAt)
                .note(req.note() != null ? req.note().trim() : null)
                .createdAt(now)
                .updatedAt(now)
                .build();

        txn = transactionRepository.save(txn);
        applyBalanceChange(groupId, wallet.getId(), txn.getType(), txn.getMoneySource(), txn.getAmount(), false);

        return buildDetailRes(txn);
    }

    @Override
    @Transactional
    public GroupTransactionDetailRes updateWithdrawal(UUID userId, UUID groupId, UUID transactionId, GroupWithdrawalReq req) {
        verifyOwnerOrTreasurer(groupId, userId);
        Group group = findActiveGroup(groupId);
        GroupWallet wallet = getActiveWallet(groupId);

        GroupTransaction txn = transactionRepository.findByIdAndGroupIdAndDeletedAtIsNull(transactionId, groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_TRANSACTION_NOT_FOUND));

        if (txn.getType() != GroupTransactionType.WITHDRAWAL) {
            throw new BusinessException(ErrorCode.TRANSACTION_TYPE_NOT_ALLOWED);
        }

        Instant occurredAt = req.resolveOccurredAt();
        if (occurredAt.isAfter(Instant.now())) {
            throw new BusinessException(ErrorCode.DATE_IN_FUTURE);
        }

        List<UUID> membersAtOccurredAt = groupMemberRepository.findMemberUserIdsAtOccurredAt(groupId, occurredAt);
        if (!membersAtOccurredAt.contains(req.fromUserId())) {
            throw new BusinessException(ErrorCode.PAYER_NOT_MEMBER);
        }

        long remainingContributionExcluding = balanceCalculator.getRemainingContribution(groupId, req.fromUserId(), txn.getId());
        if (req.amount() > remainingContributionExcluding) {
            throw new BusinessException(ErrorCode.AMOUNT_EXCEEDS_CONTRIBUTION);
        }

        if (Boolean.TRUE.equals(group.getIsSettlementEnabled())) {
            long netBalanceExcluding = balanceCalculator.getNetBalance(groupId, req.fromUserId(), txn.getId());
            if (req.amount() > netBalanceExcluding) {
                throw new BusinessException(ErrorCode.AMOUNT_EXCEEDS_SHARE);
            }
        }

        groupWalletRepository.findByGroupIdForUpdate(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_WALLET_NOT_FOUND));
        long delta = txn.getAmount() - req.amount();
        if (delta != 0) {
            groupWalletRepository.adjustBalance(wallet.getId(), delta);
        }

        Instant now = Instant.now();
        txn.setUserId(req.fromUserId());
        txn.setAmount(req.amount());
        txn.setOccurredAt(occurredAt);
        txn.setNote(req.note() != null ? req.note().trim() : null);
        txn.setReviewedBy(userId);
        txn.setReviewedAt(now);
        txn.setUpdatedAt(now);
        txn = transactionRepository.save(txn);

        return buildDetailRes(txn);
    }

    @Override
    @Transactional
    public GroupTransactionDetailRes update(UUID userId, UUID groupId, UUID transactionId, GroupTransactionUpdateReq req) {
        verifyActiveMember(groupId, userId);
        findActiveGroup(groupId);
        GroupWallet wallet = getActiveWallet(groupId);

        GroupTransaction txn = transactionRepository.findByIdAndGroupIdAndDeletedAtIsNull(transactionId, groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_TRANSACTION_NOT_FOUND));

        boolean isOwner = isOwner(groupId, userId);
        boolean isCreator = txn.getCreatedBy().equals(userId);
        if (!isCreator && !isOwner) {
            throw new BusinessException(ErrorCode.FORBIDDEN_TRANSACTION_EDIT);
        }

        if (txn.getType() != GroupTransactionType.EXPENSE && txn.getType() != GroupTransactionType.CONTRIBUTION) {
            throw new BusinessException(ErrorCode.TRANSACTION_TYPE_NOT_ALLOWED);
        }

        Instant occurredAt = req.resolveOccurredAt();
        if (occurredAt.isAfter(Instant.now())) {
            throw new BusinessException(ErrorCode.DATE_IN_FUTURE);
        }

        MoneySource moneySource = req.moneySource();
        if (txn.getType() == GroupTransactionType.CONTRIBUTION && moneySource != MoneySource.PERSONAL) {
            throw new BusinessException(ErrorCode.MONEY_SOURCE_INVALID);
        }

        UUID categoryId = null;
        if (txn.getType() == GroupTransactionType.EXPENSE) {
            if (req.categoryId() == null) {
                throw new BusinessException(ErrorCode.CATEGORY_REQUIRED_FOR_EXPENSE);
            }
            Category category = categoryRepository.findById(req.categoryId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
            if (category.getUserId() != null || !"expense".equalsIgnoreCase(category.getType())) {
                throw new BusinessException(ErrorCode.SYSTEM_CATEGORY_REQUIRED);
            }
            categoryId = category.getId();
        }

        List<UUID> membersAtOccurredAt = groupMemberRepository.findMemberUserIdsAtOccurredAt(groupId, occurredAt);
        List<GroupTransactionParticipantReq> pList = req.participants();
        boolean saveParticipants = false;

        if (txn.getType() == GroupTransactionType.EXPENSE && pList != null && !pList.isEmpty()) {
            Set<UUID> seenIds = new HashSet<>();
            for (GroupTransactionParticipantReq p : pList) {
                if (!seenIds.add(p.userId())) {
                    throw new BusinessException(ErrorCode.PARTICIPANT_NOT_MEMBER);
                }
                if (!membersAtOccurredAt.contains(p.userId())) {
                    throw new BusinessException(ErrorCode.PARTICIPANT_NOT_MEMBER);
                }
            }

            boolean anyNull = pList.stream().anyMatch(p -> p.shareAmount() == null);
            boolean anyNonNull = pList.stream().anyMatch(p -> p.shareAmount() != null);
            if (anyNull && anyNonNull) {
                throw new BusinessException(ErrorCode.PARTICIPANTS_SHARE_MIXED);
            }

            if (anyNonNull) {
                long sum = pList.stream().mapToLong(GroupTransactionParticipantReq::shareAmount).sum();
                boolean anyNonPositive = pList.stream().anyMatch(p -> p.shareAmount() <= 0);
                if (sum != req.amount() || anyNonPositive) {
                    throw new BusinessException(ErrorCode.PARTICIPANTS_SUM_MISMATCH);
                }
                saveParticipants = true;
            } else {
                if (seenIds.size() != membersAtOccurredAt.size() || !seenIds.containsAll(membersAtOccurredAt)) {
                    saveParticipants = true;
                }
            }
        }

        // Hoàn tác ảnh hưởng cũ nếu khoản cũ đang CONFIRMED
        groupWalletRepository.findByGroupIdForUpdate(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_WALLET_NOT_FOUND));

        long delta = 0L;
        if (txn.getStatus() == GroupTransactionStatus.CONFIRMED) {
            delta += calculateDelta(txn.getType(), txn.getMoneySource(), txn.getAmount(), true);
        }

        boolean isOwnerOrTreasurer = isOwner || wallet.getHeldByUserId().equals(userId);
        if (isOwnerOrTreasurer) {
            txn.setStatus(GroupTransactionStatus.CONFIRMED);
            txn.setReviewedBy(userId);
            txn.setReviewedAt(Instant.now());
            delta += calculateDelta(txn.getType(), moneySource, req.amount(), false);
        } else {
            txn.setStatus(GroupTransactionStatus.PENDING);
            txn.setReviewedBy(null);
            txn.setReviewedAt(null);
        }

        if (delta != 0L) {
            groupWalletRepository.adjustBalance(wallet.getId(), delta);
        }

        // Cập nhật participants
        participantRepository.deleteByGroupTransactionId(txn.getId());
        if (saveParticipants && pList != null) {
            List<GroupTransactionParticipant> participants = new ArrayList<>();
            for (GroupTransactionParticipantReq pr : pList) {
                participants.add(GroupTransactionParticipant.builder()
                        .groupTransactionId(txn.getId())
                        .userId(pr.userId())
                        .shareAmount(pr.shareAmount())
                        .build());
            }
            participantRepository.saveAll(participants);
        }

        txn.setMoneySource(moneySource);
        txn.setCategoryId(categoryId);
        txn.setAmount(req.amount());
        txn.setOccurredAt(occurredAt);
        txn.setNote(req.note() != null ? req.note().trim() : null);
        txn.setUpdatedAt(Instant.now());
        txn = transactionRepository.save(txn);

        return buildDetailRes(txn);
    }

    @Override
    @Transactional
    public void delete(UUID userId, UUID groupId, UUID transactionId) {
        verifyActiveMember(groupId, userId);
        findActiveGroup(groupId);
        GroupWallet wallet = getActiveWallet(groupId);

        if (!isOwner(groupId, userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_OWNER_REQUIRED);
        }

        GroupTransaction txn = transactionRepository.findByIdAndGroupIdAndDeletedAtIsNull(transactionId, groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_TRANSACTION_NOT_FOUND));

        if (txn.getStatus() == GroupTransactionStatus.CONFIRMED) {
            long delta = calculateDelta(txn.getType(), txn.getMoneySource(), txn.getAmount(), true);
            if (delta != 0L) {
                groupWalletRepository.findByGroupIdForUpdate(groupId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_WALLET_NOT_FOUND));
                groupWalletRepository.adjustBalance(wallet.getId(), delta);
            }
        }

        Instant now = Instant.now();
        txn.setDeletedAt(now);
        txn.setUpdatedAt(now);
        transactionRepository.save(txn);
    }

    @Override
    public GroupTransactionDetailRes detail(UUID userId, UUID groupId, UUID transactionId) {
        verifyActiveMember(groupId, userId);
        GroupTransaction txn = transactionRepository.findByIdAndGroupIdAndDeletedAtIsNull(transactionId, groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_TRANSACTION_NOT_FOUND));
        return buildDetailRes(txn);
    }

    @Override
    public List<GroupTransactionDetailRes> list(UUID userId, UUID groupId, GroupTransactionFilterReq filter) {
        verifyActiveMember(groupId, userId);

        String moneySource = filter.moneySource() != null ? filter.moneySource().name() : null;
        String type = filter.type() != null ? filter.type().name() : null;
        String status = filter.status() != null ? filter.status().name() : null;
        Instant fromOccurredAt = filter.resolveFrom();
        Instant toOccurredAt = filter.resolveTo();

        int limit = filter.getPageSize();
        int offset = filter.getPageNumber() * limit;

        List<GroupTransaction> txns = transactionRepository.findFiltered(
                groupId, moneySource, type, status, filter.userId(),
                fromOccurredAt, toOccurredAt, limit, offset
        );

        return txns.stream().map(this::buildDetailRes).toList();
    }

    private GroupTransactionDetailRes buildDetailRes(GroupTransaction txn) {
        List<GroupTransactionParticipant> raw = participantRepository.findByGroupTransactionId(txn.getId());
        List<GroupTransactionParticipantRes> participants = new ArrayList<>();
        boolean isAllMembers = false;

        if (!raw.isEmpty()) {
            boolean anyNull = raw.stream().anyMatch(p -> p.getShareAmount() == null);
            if (anyNull) {
                int n = raw.size();
                long base = txn.getAmount() / n;
                long rem = txn.getAmount() % n;

                List<GroupTransactionParticipant> sorted = raw.stream()
                        .sorted(Comparator.comparing(p -> p.getUserId().toString()))
                        .toList();

                for (int i = 0; i < n; i++) {
                    long share = base + (i < rem ? 1 : 0);
                    participants.add(new GroupTransactionParticipantRes(sorted.get(i).getUserId(), share));
                }
            } else {
                for (GroupTransactionParticipant p : raw) {
                    participants.add(new GroupTransactionParticipantRes(p.getUserId(), p.getShareAmount()));
                }
            }
        } else if (txn.getType() == GroupTransactionType.EXPENSE || txn.getType() == GroupTransactionType.ADJUSTMENT_DOWN || txn.getType() == GroupTransactionType.ADJUSTMENT_UP) {
            isAllMembers = true;
            List<UUID> memberUserIds = groupMemberRepository.findMemberUserIdsAtOccurredAt(txn.getGroupId(), txn.getOccurredAt());
            if (!memberUserIds.isEmpty()) {
                int n = memberUserIds.size();
                long base = txn.getAmount() / n;
                long rem = txn.getAmount() % n;

                List<UUID> sorted = memberUserIds.stream()
                        .sorted(Comparator.comparing(UUID::toString))
                        .toList();

                for (int i = 0; i < n; i++) {
                    long share = base + (i < rem ? 1 : 0);
                    participants.add(new GroupTransactionParticipantRes(sorted.get(i), share));
                }
            }
        }

        return new GroupTransactionDetailRes(
                txn.getId(),
                txn.getGroupId(),
                txn.getMoneySource(),
                txn.getUserId(),
                txn.getCreatedBy(),
                txn.getCategoryId(),
                txn.getType(),
                txn.getStatus(),
                txn.getReviewedBy(),
                txn.getReviewedAt(),
                txn.getAmount(),
                txn.getOccurredAt(),
                null, // date backward-compatible
                null, // groupWalletId
                null, // personalTransactionId
                txn.getNote(),
                txn.getCreatedAt(),
                txn.getUpdatedAt(),
                isAllMembers,
                participants
        );
    }

    private void applyBalanceChange(UUID groupId, UUID walletId, GroupTransactionType type, MoneySource source, long amount, boolean isReversal) {
        long delta = calculateDelta(type, source, amount, isReversal);
        if (delta != 0) {
            groupWalletRepository.findByGroupIdForUpdate(groupId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_WALLET_NOT_FOUND));
            groupWalletRepository.adjustBalance(walletId, delta);
        }
    }

    private long calculateDelta(GroupTransactionType type, MoneySource source, long amount, boolean isReversal) {
        long factor = isReversal ? -1L : 1L;
        return switch (type) {
            case EXPENSE -> (source == MoneySource.FUND) ? -amount * factor : 0L;
            case CONTRIBUTION -> (source == MoneySource.PERSONAL) ? amount * factor : 0L;
            case REFUND, WITHDRAWAL, ADJUSTMENT_DOWN -> -amount * factor;
            case ADJUSTMENT_UP -> amount * factor;
        };
    }

    private Group findActiveGroup(UUID groupId) {
        Group group = groupRepository.findById(groupId)
                .filter(g -> g.getStatus() != GroupStatus.DELETED)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
        if (group.getStatus() == GroupStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.GROUP_ARCHIVED);
        }
        return group;
    }

    private GroupWallet getActiveWallet(UUID groupId) {
        return groupWalletRepository.findFirstByGroupIdAndStatus(groupId, GroupWalletStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_WALLET_NOT_FOUND));
    }

    private void verifyActiveMember(UUID groupId, UUID userId) {
        boolean isMember = groupMemberRepository.existsByGroupIdAndUserIdAndStatus(
                groupId, userId, MemberStatus.ACTIVE
        );
        if (!isMember) {
            throw new BusinessException(ErrorCode.FORBIDDEN_NOT_GROUP_MEMBER);
        }
    }

    private void verifyOwnerOrTreasurer(UUID groupId, UUID userId) {
        GroupWallet wallet = getActiveWallet(groupId);
        boolean isOwner = isOwner(groupId, userId);
        boolean isTreasurer = wallet.getHeldByUserId().equals(userId);
        if (!isOwner && !isTreasurer) {
            throw new BusinessException(ErrorCode.FORBIDDEN_TREASURER_REQUIRED);
        }
    }

    private boolean isOwner(UUID groupId, UUID userId) {
        return groupMemberRepository.existsByGroupIdAndUserIdAndRoleAndStatus(
                groupId, userId, GroupRole.OWNER, MemberStatus.ACTIVE
        );
    }
}

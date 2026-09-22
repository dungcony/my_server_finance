package com.datn.financeapp.group.service.impl;

import com.datn.financeapp.category.service.CategoryService;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.request.transaction.GroupRefundReq;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionBulkReviewReq;
import com.datn.financeapp.group.dto.response.transaction.GroupTransactionBulkReviewRes;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionCreateReq;
import com.datn.financeapp.group.dto.response.transaction.GroupTransactionDetailRes;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionFilterReq;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionParticipantReq;
import com.datn.financeapp.group.dto.response.transaction.GroupTransactionParticipantRes;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionUpdateReq;
import com.datn.financeapp.group.dto.request.transaction.GroupWithdrawalReq;
import com.datn.financeapp.group.entity.Group;
import com.datn.financeapp.group.entity.GroupTransaction;
import com.datn.financeapp.group.entity.GroupTransactionParticipant;
import com.datn.financeapp.group.entity.GroupWallet;
import com.datn.financeapp.group.enums.GroupTransactionStatus;
import com.datn.financeapp.group.enums.GroupTransactionType;
import com.datn.financeapp.group.enums.GroupWalletStatus;
import com.datn.financeapp.group.enums.MemberStatus;
import com.datn.financeapp.group.enums.MoneySource;
import com.datn.financeapp.group.repository.GroupMemberRepository;
import com.datn.financeapp.group.repository.GroupTransactionParticipantRepository;
import com.datn.financeapp.group.helper.GroupSplitHelper;
import com.datn.financeapp.group.mapper.GroupTransactionMapper;
import com.datn.financeapp.group.repository.GroupTransactionRepository;
import com.datn.financeapp.group.repository.GroupWalletRepository;
import com.datn.financeapp.group.service.GroupBalanceService;
import com.datn.financeapp.group.service.GroupTransactionService;
import com.datn.financeapp.group.validator.GroupPermissionValidator;
import java.time.Instant;
import java.util.ArrayList;
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
    private final GroupMemberRepository groupMemberRepository;
    private final GroupWalletRepository groupWalletRepository;
    private final CategoryService categoryService;
    private final GroupBalanceService balanceService;
    private final GroupPermissionValidator permissionValidator;
    private final GroupTransactionMapper transactionMapper;
    private final GroupSplitHelper splitHelper;

    @Override
    @Transactional
    public GroupTransactionDetailRes create(UUID userId, UUID groupId, GroupTransactionCreateReq req) {
        verifyActiveMember(groupId, userId);
        findActiveGroup(groupId);
        GroupWallet wallet = getActiveWallet(groupId);

        if (req.type() != GroupTransactionType.EXPENSE && req.type() != GroupTransactionType.CONTRIBUTION) {
            throw new BusinessException(ErrorCode.TRANSACTION_TYPE_NOT_ALLOWED);
        }

        Instant occurredAt = req.resolveOccurredAt();
        validateOccurredAt(occurredAt);

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
            categoryId = validateExpenseCategory(req.categoryId());
        }

        UUID targetUserId = req.userId() != null ? req.userId() : userId;
        List<UUID> membersAtOccurredAt = groupMemberRepository.findMemberUserIdsAtOccurredAt(groupId, occurredAt);

        if (req.type() == GroupTransactionType.EXPENSE) {
            verifyMemberAtOccurredAt(membersAtOccurredAt, targetUserId);
        } else {
            // CONTRIBUTION: cho phép cựu thành viên nộp bù nếu đang có phần âm
            if (!membersAtOccurredAt.contains(targetUserId)) {
                boolean wasMember = groupMemberRepository.findByGroupIdAndUserIdAndStatusIn(
                        groupId, targetUserId, List.of(MemberStatus.LEFT, MemberStatus.REMOVED)
                ).isPresent();
                long netBalance = balanceService.getNetBalance(groupId, targetUserId, null);
                if (!wasMember || netBalance >= 0) {
                    throw new BusinessException(ErrorCode.PAYER_NOT_MEMBER);
                }
            }
        }

        // Validate participants cho EXPENSE
        List<GroupTransactionParticipantReq> pList = req.participants();
        boolean saveParticipants = (req.type() == GroupTransactionType.EXPENSE)
                && validateParticipants(pList, membersAtOccurredAt, req.amount());

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

        if (saveParticipants) {
            saveParticipants(txn.getId(), pList);
        }

        // Cập nhật số dư quỹ nếu CONFIRMED
        if (status == GroupTransactionStatus.CONFIRMED) {
            applyBalanceChange(groupId, wallet.getId(), txn.getType(), txn.getMoneySource(), txn.getAmount());
        }

        return buildDetailRes(txn);
    }

    @Override
    @Transactional
    public GroupTransactionDetailRes confirm(UUID userId, UUID groupId, UUID transactionId) {
        GroupWallet wallet = verifyOwnerOrTreasurer(groupId, userId);
        findActiveGroup(groupId);

        GroupTransaction txn = findActiveTransaction(transactionId, groupId);

        if (txn.getStatus() != GroupTransactionStatus.PENDING) {
            throw new BusinessException(ErrorCode.TRANSACTION_NOT_PENDING);
        }

        updateReviewStatus(txn, GroupTransactionStatus.CONFIRMED, userId, Instant.now());
        applyBalanceChange(groupId, wallet.getId(), txn.getType(), txn.getMoneySource(), txn.getAmount());
        txn = transactionRepository.save(txn);

        return buildDetailRes(txn);
    }

    @Override
    @Transactional
    public GroupTransactionDetailRes reject(UUID userId, UUID groupId, UUID transactionId) {
        verifyOwnerOrTreasurer(groupId, userId);
        findActiveGroup(groupId);

        GroupTransaction txn = findActiveTransaction(transactionId, groupId);

        if (txn.getStatus() != GroupTransactionStatus.PENDING) {
            throw new BusinessException(ErrorCode.TRANSACTION_NOT_PENDING);
        }

        updateReviewStatus(txn, GroupTransactionStatus.REJECTED, userId, Instant.now());
        txn = transactionRepository.save(txn);

        return buildDetailRes(txn);
    }

    @Override
    @Transactional
    public GroupTransactionBulkReviewRes bulkConfirm(UUID userId, UUID groupId, GroupTransactionBulkReviewReq req) {
        GroupWallet wallet = verifyOwnerOrTreasurer(groupId, userId);
        findActiveGroup(groupId);

        List<UUID> ids = req.transactionIds();
        List<GroupTransaction> txns = fetchPendingTransactionsForBulkReview(groupId, ids);
        if (txns.isEmpty()) {
            return new GroupTransactionBulkReviewRes(0, 0, 0, List.of());
        }

        long totalDelta = 0L;
        Instant now = Instant.now();
        for (GroupTransaction t : txns) {
            updateReviewStatus(t, GroupTransactionStatus.CONFIRMED, userId, now);
            totalDelta += calculateDelta(t.getType(), t.getMoneySource(), t.getAmount(), false);
        }

        adjustWalletBalance(groupId, wallet.getId(), totalDelta);
        transactionRepository.saveAll(txns);

        return new GroupTransactionBulkReviewRes(ids.size(), txns.size(), 0, ids);
    }

    @Override
    @Transactional
    public GroupTransactionBulkReviewRes bulkReject(UUID userId, UUID groupId, GroupTransactionBulkReviewReq req) {
        verifyOwnerOrTreasurer(groupId, userId);
        findActiveGroup(groupId);

        List<UUID> ids = req.transactionIds();
        List<GroupTransaction> txns = fetchPendingTransactionsForBulkReview(groupId, ids);
        if (txns.isEmpty()) {
            return new GroupTransactionBulkReviewRes(0, 0, 0, List.of());
        }

        Instant now = Instant.now();
        for (GroupTransaction t : txns) {
            updateReviewStatus(t, GroupTransactionStatus.REJECTED, userId, now);
        }
        transactionRepository.saveAll(txns);

        return new GroupTransactionBulkReviewRes(ids.size(), txns.size(), 0, ids);
    }

    @Override
    @Transactional
    public GroupTransactionDetailRes createRefund(UUID userId, UUID groupId, GroupRefundReq req) {
        GroupWallet wallet = verifyOwnerOrTreasurer(groupId, userId);
        Group group = findActiveGroup(groupId);

        Instant occurredAt = req.resolveOccurredAt();
        validateOccurredAt(occurredAt);

        List<UUID> membersAtOccurredAt = groupMemberRepository.findMemberUserIdsAtOccurredAt(groupId, occurredAt);
        verifyMemberAtOccurredAt(membersAtOccurredAt, req.toUserId());

        validateSettlementNetBalance(group, groupId, req.toUserId(), req.amount(), null);

        return createAndSaveDirectTransaction(
                groupId, wallet.getId(), userId, req.toUserId(),
                GroupTransactionType.REFUND, req.amount(), occurredAt, req.note()
        );
    }

    @Override
    @Transactional
    public GroupTransactionDetailRes updateRefund(UUID userId, UUID groupId, UUID transactionId, GroupRefundReq req) {
        GroupWallet wallet = verifyOwnerOrTreasurer(groupId, userId);
        Group group = findActiveGroup(groupId);

        GroupTransaction txn = findActiveTransaction(transactionId, groupId);
        if (txn.getType() != GroupTransactionType.REFUND) {
            throw new BusinessException(ErrorCode.TRANSACTION_TYPE_NOT_ALLOWED);
        }

        Instant occurredAt = req.resolveOccurredAt();
        validateOccurredAt(occurredAt);

        List<UUID> membersAtOccurredAt = groupMemberRepository.findMemberUserIdsAtOccurredAt(groupId, occurredAt);
        verifyMemberAtOccurredAt(membersAtOccurredAt, req.toUserId());

        validateSettlementNetBalance(group, groupId, req.toUserId(), req.amount(), txn.getId());

        long delta = txn.getAmount() - req.amount();
        adjustWalletBalance(groupId, wallet.getId(), delta);

        return updateAndSaveDirectTransaction(txn, req.toUserId(), req.amount(), occurredAt, req.note(), userId);
    }

    @Override
    @Transactional
    public GroupTransactionDetailRes createWithdrawal(UUID userId, UUID groupId, GroupWithdrawalReq req) {
        GroupWallet wallet = verifyOwnerOrTreasurer(groupId, userId);
        Group group = findActiveGroup(groupId);

        Instant occurredAt = req.resolveOccurredAt();
        validateOccurredAt(occurredAt);

        List<UUID> membersAtOccurredAt = groupMemberRepository.findMemberUserIdsAtOccurredAt(groupId, occurredAt);
        verifyMemberAtOccurredAt(membersAtOccurredAt, req.fromUserId());

        validateRemainingContribution(groupId, req.fromUserId(), req.amount(), null);
        validateSettlementNetBalance(group, groupId, req.fromUserId(), req.amount(), null);

        return createAndSaveDirectTransaction(
                groupId, wallet.getId(), userId, req.fromUserId(),
                GroupTransactionType.WITHDRAWAL, req.amount(), occurredAt, req.note()
        );
    }

    @Override
    @Transactional
    public GroupTransactionDetailRes updateWithdrawal(UUID userId, UUID groupId, UUID transactionId, GroupWithdrawalReq req) {
        GroupWallet wallet = verifyOwnerOrTreasurer(groupId, userId);
        Group group = findActiveGroup(groupId);

        GroupTransaction txn = findActiveTransaction(transactionId, groupId);
        if (txn.getType() != GroupTransactionType.WITHDRAWAL) {
            throw new BusinessException(ErrorCode.TRANSACTION_TYPE_NOT_ALLOWED);
        }

        Instant occurredAt = req.resolveOccurredAt();
        validateOccurredAt(occurredAt);

        List<UUID> membersAtOccurredAt = groupMemberRepository.findMemberUserIdsAtOccurredAt(groupId, occurredAt);
        verifyMemberAtOccurredAt(membersAtOccurredAt, req.fromUserId());

        validateRemainingContribution(groupId, req.fromUserId(), req.amount(), txn.getId());
        validateSettlementNetBalance(group, groupId, req.fromUserId(), req.amount(), txn.getId());

        long delta = txn.getAmount() - req.amount();
        adjustWalletBalance(groupId, wallet.getId(), delta);

        return updateAndSaveDirectTransaction(txn, req.fromUserId(), req.amount(), occurredAt, req.note(), userId);
    }

    @Override
    @Transactional
    public GroupTransactionDetailRes update(UUID userId, UUID groupId, UUID transactionId, GroupTransactionUpdateReq req) {
        verifyActiveMember(groupId, userId);
        findActiveGroup(groupId);
        GroupWallet wallet = getActiveWallet(groupId);

        GroupTransaction txn = findActiveTransaction(transactionId, groupId);

        boolean isOwner = isOwner(groupId, userId);
        boolean isCreator = txn.getCreatedBy().equals(userId);
        if (!isCreator && !isOwner) {
            throw new BusinessException(ErrorCode.FORBIDDEN_TRANSACTION_EDIT);
        }

        if (txn.getType() != GroupTransactionType.EXPENSE && txn.getType() != GroupTransactionType.CONTRIBUTION) {
            throw new BusinessException(ErrorCode.TRANSACTION_TYPE_NOT_ALLOWED);
        }

        Instant occurredAt = req.resolveOccurredAt();
        validateOccurredAt(occurredAt);

        MoneySource moneySource = req.moneySource();
        if (txn.getType() == GroupTransactionType.CONTRIBUTION && moneySource != MoneySource.PERSONAL) {
            throw new BusinessException(ErrorCode.MONEY_SOURCE_INVALID);
        }

        UUID categoryId = null;
        if (txn.getType() == GroupTransactionType.EXPENSE) {
            categoryId = validateExpenseCategory(req.categoryId());
        }

        List<UUID> membersAtOccurredAt = groupMemberRepository.findMemberUserIdsAtOccurredAt(groupId, occurredAt);
        List<GroupTransactionParticipantReq> pList = req.participants();
        boolean saveParticipants = (txn.getType() == GroupTransactionType.EXPENSE)
                && validateParticipants(pList, membersAtOccurredAt, req.amount());

        // Hoàn tác ảnh hưởng cũ nếu khoản cũ đang CONFIRMED
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

        adjustWalletBalance(groupId, wallet.getId(), delta);

        // Cập nhật participants
        participantRepository.deleteByGroupTransactionId(txn.getId());
        if (saveParticipants) {
            saveParticipants(txn.getId(), pList);
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

        GroupTransaction txn = findActiveTransaction(transactionId, groupId);

        if (txn.getStatus() == GroupTransactionStatus.CONFIRMED) {
            long delta = calculateDelta(txn.getType(), txn.getMoneySource(), txn.getAmount(), true);
            adjustWalletBalance(groupId, wallet.getId(), delta);
        }

        Instant now = Instant.now();
        txn.setDeletedAt(now);
        txn.setUpdatedAt(now);
        transactionRepository.save(txn);
    }

    @Override
    public GroupTransactionDetailRes detail(UUID userId, UUID groupId, UUID transactionId) {
        verifyActiveMember(groupId, userId);
        GroupTransaction txn = findActiveTransaction(transactionId, groupId);
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
                List<UUID> userIds = raw.stream().map(GroupTransactionParticipant::getUserId).toList();
                participants = splitHelper.splitEvenly(txn.getAmount(), userIds);
            } else {
                participants = raw.stream()
                        .map(transactionMapper::toParticipantResponse)
                        .toList();
            }
        } else if (txn.getType() == GroupTransactionType.EXPENSE || txn.getType() == GroupTransactionType.ADJUSTMENT_DOWN || txn.getType() == GroupTransactionType.ADJUSTMENT_UP) {
            isAllMembers = true;
            List<UUID> memberUserIds = groupMemberRepository.findMemberUserIdsAtOccurredAt(txn.getGroupId(), txn.getOccurredAt());
            participants = splitHelper.splitEvenly(txn.getAmount(), memberUserIds);
        }

        return transactionMapper.toDetailResponse(txn, isAllMembers, participants);
    }

    private void adjustWalletBalance(UUID groupId, UUID walletId, long delta) {
        if (delta != 0L) {
            groupWalletRepository.findByGroupIdForUpdate(groupId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_WALLET_NOT_FOUND));
            groupWalletRepository.adjustBalance(walletId, delta);
        }
    }

    private void applyBalanceChange(UUID groupId, UUID walletId, GroupTransactionType type, MoneySource source, long amount) {
        long delta = calculateDelta(type, source, amount, false);
        adjustWalletBalance(groupId, walletId, delta);
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
        return permissionValidator.validateAndGetActiveGroup(groupId);
    }

    private GroupWallet getActiveWallet(UUID groupId) {
        return groupWalletRepository.findFirstByGroupIdAndStatus(groupId, GroupWalletStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_WALLET_NOT_FOUND));
    }

    private void verifyActiveMember(UUID groupId, UUID userId) {
        permissionValidator.verifyActiveMember(groupId, userId);
    }

    private GroupWallet verifyOwnerOrTreasurer(UUID groupId, UUID userId) {
        GroupWallet wallet = getActiveWallet(groupId);
        permissionValidator.verifyOwnerOrTreasurer(groupId, userId, wallet.getHeldByUserId());
        return wallet;
    }

    private boolean isOwner(UUID groupId, UUID userId) {
        return permissionValidator.isOwner(groupId, userId);
    }

    private GroupTransaction findActiveTransaction(UUID transactionId, UUID groupId) {
        return transactionRepository.findByIdAndGroupIdAndDeletedAtIsNull(transactionId, groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_TRANSACTION_NOT_FOUND));
    }

    private void validateOccurredAt(Instant occurredAt) {
        if (occurredAt.isAfter(Instant.now())) {
            throw new BusinessException(ErrorCode.DATE_IN_FUTURE);
        }
    }

    private void verifyMemberAtOccurredAt(List<UUID> membersAtOccurredAt, UUID targetUserId) {
        if (!membersAtOccurredAt.contains(targetUserId)) {
            throw new BusinessException(ErrorCode.PAYER_NOT_MEMBER);
        }
    }

    private UUID validateExpenseCategory(UUID categoryId) {
        if (categoryId == null) {
            throw new BusinessException(ErrorCode.CATEGORY_REQUIRED_FOR_EXPENSE);
        }
        categoryService.validateSystemExpenseCategory(categoryId);
        return categoryId;
    }

    private boolean validateParticipants(
            List<GroupTransactionParticipantReq> pList,
            List<UUID> membersAtOccurredAt,
            long amount
    ) {
        if (pList == null || pList.isEmpty()) {
            return false;
        }

        Set<UUID> seenIds = new HashSet<>();
        for (GroupTransactionParticipantReq p : pList) {
            if (!seenIds.add(p.userId()) || !membersAtOccurredAt.contains(p.userId())) {
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
            if (sum != amount || anyNonPositive) {
                throw new BusinessException(ErrorCode.PARTICIPANTS_SUM_MISMATCH);
            }
            return true;
        }

        return seenIds.size() != membersAtOccurredAt.size() || !seenIds.containsAll(membersAtOccurredAt);
    }

    private void saveParticipants(UUID transactionId, List<GroupTransactionParticipantReq> pList) {
        if (pList == null || pList.isEmpty()) {
            return;
        }
        List<GroupTransactionParticipant> participants = new ArrayList<>();
        for (GroupTransactionParticipantReq pr : pList) {
            participants.add(GroupTransactionParticipant.builder()
                    .groupTransactionId(transactionId)
                    .userId(pr.userId())
                    .shareAmount(pr.shareAmount())
                    .build());
        }
        participantRepository.saveAll(participants);
    }

    private List<GroupTransaction> fetchPendingTransactionsForBulkReview(UUID groupId, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<GroupTransaction> txns = transactionRepository.findByIdInAndGroupIdAndDeletedAtIsNull(ids, groupId);
        if (txns.size() != ids.size() || txns.stream().anyMatch(t -> t.getStatus() != GroupTransactionStatus.PENDING)) {
            throw new BusinessException(ErrorCode.TRANSACTION_NOT_PENDING);
        }
        return txns;
    }

    private void updateReviewStatus(GroupTransaction txn, GroupTransactionStatus status, UUID reviewerId, Instant timestamp) {
        txn.setStatus(status);
        txn.setReviewedBy(reviewerId);
        txn.setReviewedAt(timestamp);
        txn.setUpdatedAt(timestamp);
    }

    private void validateSettlementNetBalance(Group group, UUID groupId, UUID targetUserId, long amount, UUID excludeTxnId) {
        if (Boolean.TRUE.equals(group.getIsSettlementEnabled())) {
            long netBalance = balanceService.getNetBalance(groupId, targetUserId, excludeTxnId);
            if (amount > netBalance) {
                throw new BusinessException(ErrorCode.AMOUNT_EXCEEDS_SHARE);
            }
        }
    }

    private void validateRemainingContribution(UUID groupId, UUID targetUserId, long amount, UUID excludeTxnId) {
        long remaining = balanceService.getRemainingContribution(groupId, targetUserId, excludeTxnId);
        if (amount > remaining) {
            throw new BusinessException(ErrorCode.AMOUNT_EXCEEDS_CONTRIBUTION);
        }
    }

    private GroupTransactionDetailRes updateAndSaveDirectTransaction(
            GroupTransaction txn,
            UUID targetUserId,
            long amount,
            Instant occurredAt,
            String note,
            UUID reviewerId
    ) {
        Instant now = Instant.now();
        txn.setUserId(targetUserId);
        txn.setAmount(amount);
        txn.setOccurredAt(occurredAt);
        txn.setNote(note != null ? note.trim() : null);
        txn.setReviewedBy(reviewerId);
        txn.setReviewedAt(now);
        txn.setUpdatedAt(now);
        GroupTransaction savedTxn = transactionRepository.save(txn);
        return buildDetailRes(savedTxn);
    }

    private GroupTransactionDetailRes createAndSaveDirectTransaction(
            UUID groupId,
            UUID walletId,
            UUID creatorId,
            UUID targetUserId,
            GroupTransactionType type,
            long amount,
            Instant occurredAt,
            String note
    ) {
        Instant now = Instant.now();
        GroupTransaction txn = GroupTransaction.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .moneySource(MoneySource.FUND)
                .userId(targetUserId)
                .createdBy(creatorId)
                .categoryId(null)
                .type(type)
                .status(GroupTransactionStatus.CONFIRMED)
                .reviewedBy(creatorId)
                .reviewedAt(now)
                .amount(amount)
                .occurredAt(occurredAt)
                .note(note != null ? note.trim() : null)
                .createdAt(now)
                .updatedAt(now)
                .build();

        txn = transactionRepository.save(txn);
        applyBalanceChange(groupId, walletId, txn.getType(), txn.getMoneySource(), txn.getAmount());
        return buildDetailRes(txn);
    }
}

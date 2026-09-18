package com.datn.financeapp.group.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.wallet.GroupWalletCreateReq;
import com.datn.financeapp.group.dto.wallet.GroupWalletReconcileReq;
import com.datn.financeapp.group.dto.wallet.GroupWalletReconcileRes;
import com.datn.financeapp.group.dto.wallet.GroupWalletRes;
import com.datn.financeapp.group.dto.wallet.GroupWalletUpdateReq;
import com.datn.financeapp.group.entity.Group;
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
import com.datn.financeapp.group.service.GroupWalletService;
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
public class GroupWalletServiceImpl implements GroupWalletService {

    private final GroupWalletRepository groupWalletRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupRepository groupRepository;
    private final GroupTransactionRepository groupTransactionRepository;
    private final GroupTransactionParticipantRepository participantRepository;

    @Override
    public GroupWalletRes getFund(UUID userId, UUID groupId) {
        verifyGroupMember(groupId, userId);
        GroupWallet wallet = groupWalletRepository.findFirstByGroupId(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_WALLET_NOT_FOUND));
        return mapToRes(wallet);
    }

    @Override
    @Transactional
    public GroupWalletRes updateFund(UUID userId, UUID groupId, GroupWalletUpdateReq req) {
        verifyOwnerRole(groupId, userId);

        Group group = groupRepository.findById(groupId)
                .filter(g -> g.getStatus() != GroupStatus.DELETED)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

        if (group.getStatus() == GroupStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.GROUP_ARCHIVED);
        }

        GroupWallet wallet = groupWalletRepository.findFirstByGroupId(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_WALLET_NOT_FOUND));

        if (req.name() != null && !req.name().isBlank()) {
            wallet.setName(req.name().trim());
        }
        if (req.heldByUserId() != null) {
            boolean isHeldUserMember = groupMemberRepository.existsByGroupIdAndUserIdAndStatus(
                    groupId, req.heldByUserId(), MemberStatus.ACTIVE
            );
            if (!isHeldUserMember) {
                throw new BusinessException(ErrorCode.FORBIDDEN_NOT_GROUP_MEMBER);
            }
            wallet.setHeldByUserId(req.heldByUserId());
        }
        if (req.status() != null) {
            wallet.setStatus(req.status());
        }
        wallet = groupWalletRepository.save(wallet);

        return mapToRes(wallet);
    }

    @Override
    @Transactional
    public GroupWalletReconcileRes reconcileFund(UUID userId, UUID groupId, GroupWalletReconcileReq req) {
        Group group = groupRepository.findById(groupId)
                .filter(g -> g.getStatus() != GroupStatus.DELETED)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

        if (group.getStatus() == GroupStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.GROUP_ARCHIVED);
        }

        GroupWallet wallet = groupWalletRepository.findFirstByGroupId(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_WALLET_NOT_FOUND));

        return executeReconcile(userId, groupId, wallet, req);
    }

    @Override
    @Transactional
    public GroupWalletRes create(UUID userId, UUID groupId, GroupWalletCreateReq req) {
        // Tương thích ngược: nếu nhóm đã có quỹ thì trả về quỹ đó
        return groupWalletRepository.findFirstByGroupId(groupId)
                .map(this::mapToRes)
                .orElseGet(() -> {
                    verifyOwnerRole(groupId, userId);
                    Instant now = Instant.now();
                    GroupWallet wallet = GroupWallet.builder()
                            .id(UUID.randomUUID())
                            .groupId(groupId)
                            .heldByUserId(req.heldByUserId() != null ? req.heldByUserId() : userId)
                            .name(req.name().trim())
                            .currentBalance(0L)
                            .status(GroupWalletStatus.ACTIVE)
                            .createdAt(now)
                            .build();
                    return mapToRes(groupWalletRepository.save(wallet));
                });
    }

    @Override
    public List<GroupWalletRes> list(UUID userId, UUID groupId) {
        verifyGroupMember(groupId, userId);
        return groupWalletRepository.findFirstByGroupId(groupId)
                .map(w -> List.of(mapToRes(w)))
                .orElse(List.of());
    }

    @Override
    public GroupWalletRes detail(UUID userId, UUID groupId, UUID walletId) {
        return getFund(userId, groupId);
    }

    @Override
    @Transactional
    public GroupWalletRes update(UUID userId, UUID groupId, UUID walletId, GroupWalletUpdateReq req) {
        return updateFund(userId, groupId, req);
    }

    @Override
    @Transactional
    public void adjustBalance(UUID walletId, Long delta) {
        groupWalletRepository.adjustBalance(walletId, delta);
    }

    @Override
    @Transactional
    public GroupWalletReconcileRes reconcile(UUID userId, UUID groupId, UUID walletId, GroupWalletReconcileReq req) {
        return reconcileFund(userId, groupId, req);
    }

    private GroupWalletReconcileRes executeReconcile(UUID userId, UUID groupId, GroupWallet wallet, GroupWalletReconcileReq req) {
        boolean isTreasurer = wallet.getHeldByUserId().equals(userId);
        boolean isOwner = groupMemberRepository.existsByGroupIdAndUserIdAndRoleAndStatus(
                groupId, userId, GroupRole.OWNER, MemberStatus.ACTIVE
        );
        if (!isTreasurer && !isOwner) {
            throw new BusinessException(ErrorCode.FORBIDDEN_TREASURER_REQUIRED);
        }

        long previousBalance = wallet.getCurrentBalance();
        long actualBalance = req.actualBalance();
        long difference = actualBalance - previousBalance;

        if (difference == 0) {
            return new GroupWalletReconcileRes(
                    wallet.getId(),
                    previousBalance,
                    actualBalance,
                    0L,
                    null,
                    null
            );
        }

        GroupTransactionType adjustmentType = difference > 0
                ? GroupTransactionType.ADJUSTMENT_UP
                : GroupTransactionType.ADJUSTMENT_DOWN;
        long amount = Math.abs(difference);

        Instant now = Instant.now();
        UUID transactionId = UUID.randomUUID();

        GroupTransaction transaction = GroupTransaction.builder()
                .id(transactionId)
                .groupId(groupId)
                .moneySource(MoneySource.FUND)
                .userId(wallet.getHeldByUserId())
                .createdBy(userId)
                .categoryId(null)
                .type(adjustmentType)
                .status(GroupTransactionStatus.CONFIRMED)
                .reviewedBy(userId)
                .reviewedAt(now)
                .amount(amount)
                .occurredAt(now)
                .note(req.note() != null && !req.note().isBlank() ? req.note().trim() : "Kiểm kê số dư thực tế")
                .createdAt(now)
                .updatedAt(now)
                .build();
        groupTransactionRepository.save(transaction);

        // Cập nhật số dư quỹ
        wallet.setCurrentBalance(actualBalance);
        groupWalletRepository.save(wallet);

        // Xử lý người chịu độ lệch nếu có excludedUserIds
        if (req.excludedUserIds() != null && !req.excludedUserIds().isEmpty()) {
            Set<UUID> excludedSet = new HashSet<>(req.excludedUserIds());
            List<UUID> memberIdsAtOccurred = groupMemberRepository.findMemberUserIdsAtOccurredAt(groupId, now);
            List<UUID> includedUserIds = memberIdsAtOccurred.stream()
                    .filter(uid -> !excludedSet.contains(uid))
                    .toList();

            if (!includedUserIds.isEmpty()) {
                List<GroupTransactionParticipant> participants = new ArrayList<>();
                for (UUID uid : includedUserIds) {
                    participants.add(GroupTransactionParticipant.builder()
                            .groupTransactionId(transactionId)
                            .userId(uid)
                            .shareAmount(null)
                            .build());
                }
                participantRepository.saveAll(participants);
            }
        }

        return new GroupWalletReconcileRes(
                wallet.getId(),
                previousBalance,
                actualBalance,
                difference,
                adjustmentType,
                transactionId
        );
    }

    private void verifyGroupMember(UUID groupId, UUID userId) {
        boolean isMember = groupMemberRepository.existsByGroupIdAndUserIdAndStatus(
                groupId, userId, MemberStatus.ACTIVE
        );
        if (!isMember) {
            throw new BusinessException(ErrorCode.FORBIDDEN_NOT_GROUP_MEMBER);
        }
    }

    private void verifyOwnerRole(UUID groupId, UUID userId) {
        boolean isOwner = groupMemberRepository.existsByGroupIdAndUserIdAndRoleAndStatus(
                groupId, userId, GroupRole.OWNER, MemberStatus.ACTIVE
        );
        if (!isOwner) {
            throw new BusinessException(ErrorCode.FORBIDDEN_OWNER_REQUIRED);
        }
    }

    private GroupWalletRes mapToRes(GroupWallet wallet) {
        return new GroupWalletRes(
                wallet.getId(),
                wallet.getGroupId(),
                wallet.getHeldByUserId(),
                wallet.getName(),
                0L,
                wallet.getCurrentBalance(),
                wallet.getStatus(),
                wallet.getCreatedAt()
        );
    }
}

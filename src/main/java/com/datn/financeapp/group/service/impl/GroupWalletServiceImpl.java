package com.datn.financeapp.group.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.request.wallet.GroupWalletReconcileReq;
import com.datn.financeapp.group.dto.response.wallet.GroupWalletReconcileRes;
import com.datn.financeapp.group.dto.response.wallet.GroupWalletRes;
import com.datn.financeapp.group.dto.request.wallet.GroupWalletUpdateReq;
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
import com.datn.financeapp.group.repository.GroupTransactionRepository;
import com.datn.financeapp.group.repository.GroupWalletRepository;
import com.datn.financeapp.group.mapper.GroupWalletMapper;
import com.datn.financeapp.group.service.GroupWalletService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.datn.financeapp.group.validator.GroupPermissionValidator;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupWalletServiceImpl implements GroupWalletService {

    private final GroupWalletRepository groupWalletRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupTransactionRepository groupTransactionRepository;
    private final GroupTransactionParticipantRepository participantRepository;
    private final GroupPermissionValidator permissionValidator;
    private final GroupWalletMapper walletMapper;

    @Override
    public GroupWalletRes getWallet(UUID userId, UUID groupId) {
        permissionValidator.verifyActiveMember(groupId, userId);
        GroupWallet wallet = groupWalletRepository.findByGroupId(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_WALLET_NOT_FOUND));
        return walletMapper.toResponse(wallet);
    }

    @Override
    @Transactional
    public GroupWalletRes updateFund(UUID userId, UUID groupId, GroupWalletUpdateReq req) {
        permissionValidator.verifyOwnerRole(groupId, userId);

        GroupWallet wallet = groupWalletRepository.findByGroupId(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_WALLET_NOT_FOUND));

        if (req.heldByUserId() != null) {
            boolean isMember = groupMemberRepository.existsByGroupIdAndUserIdAndStatus(
                    groupId, req.heldByUserId(), MemberStatus.ACTIVE
            );
            if (!isMember) {
                throw new BusinessException(ErrorCode.FORBIDDEN_NOT_GROUP_MEMBER);
            }
            wallet.setHeldByUserId(req.heldByUserId());
        }

        wallet = groupWalletRepository.save(wallet);
        return walletMapper.toResponse(wallet);
    }

    @Override
    @Transactional
    public GroupWalletReconcileRes reconcileFund(UUID userId, UUID groupId, GroupWalletReconcileReq req) {

        GroupWallet wallet = groupWalletRepository.findByGroupId(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_WALLET_NOT_FOUND));

        return executeReconcile(userId, groupId, wallet, req);
    }

    @Override
    @Transactional
    public void adjustBalance(UUID walletId, Long delta) {
        groupWalletRepository.adjustBalance(walletId, delta);
    }

    private GroupWalletReconcileRes executeReconcile(UUID userId, UUID groupId, GroupWallet wallet, GroupWalletReconcileReq req) {
        permissionValidator.verifyOwnerOrTreasurer(groupId, userId, wallet.getHeldByUserId());

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
}

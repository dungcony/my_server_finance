package com.datn.financeapp.group.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.group.GroupCreateReq;
import com.datn.financeapp.group.dto.group.GroupDetailRes;
import com.datn.financeapp.group.dto.group.GroupInviteCodeRes;
import com.datn.financeapp.group.dto.group.GroupJoinReq;
import com.datn.financeapp.group.dto.group.GroupMemberRes;
import com.datn.financeapp.group.dto.group.GroupSummaryRes;
import com.datn.financeapp.group.dto.group.GroupUpdateReq;
import com.datn.financeapp.group.dto.wallet.GroupWalletRes;
import com.datn.financeapp.group.entity.Group;
import com.datn.financeapp.group.entity.GroupMember;
import com.datn.financeapp.group.entity.GroupWallet;
import com.datn.financeapp.group.enums.GroupRole;
import com.datn.financeapp.group.enums.GroupStatus;
import com.datn.financeapp.group.enums.GroupTransactionStatus;
import com.datn.financeapp.group.enums.GroupWalletStatus;
import com.datn.financeapp.group.enums.MemberStatus;
import com.datn.financeapp.group.repository.GroupMemberRepository;
import com.datn.financeapp.group.repository.GroupRepository;
import com.datn.financeapp.group.repository.GroupTransactionRepository;
import com.datn.financeapp.group.repository.GroupWalletRepository;
import com.datn.financeapp.group.service.GroupService;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupServiceImpl implements GroupService {

    private static final String INVITE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupWalletRepository groupWalletRepository;
    private final GroupTransactionRepository groupTransactionRepository;

    @Override
    @Transactional
    public GroupDetailRes create(UUID userId, GroupCreateReq req) {
        Instant now = Instant.now();
        UUID groupId = UUID.randomUUID();

        String inviteCode = generateUniqueInviteCode();
        Instant inviteExpiresAt = now.plus(7, ChronoUnit.DAYS);

        boolean settlementEnabled = Boolean.TRUE.equals(req.isSettlementEnabled());
        Group group = Group.builder()
                .id(groupId)
                .name(req.name())
                .description(req.description())
                .status(GroupStatus.ACTIVE)
                .target(req.target())
                .isSettlementEnabled(settlementEnabled)
                .isJoinWithoutConfirm(req.isJoinWithoutConfirm())
                .inviteCode(inviteCode)
                .inviteCodeExpiresAt(inviteExpiresAt)
                .createdAt(now)
                .updatedAt(now)
                .build();
        group = groupRepository.save(group);

        GroupMember member = GroupMember.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .userId(userId)
                .role(GroupRole.OWNER)
                .status(MemberStatus.ACTIVE)
                .joinedAt(now)
                .build();
        member = groupMemberRepository.save(member);

        GroupWallet wallet = GroupWallet.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .heldByUserId(userId)
                .name(req.getWalletName())
                .currentBalance(0L)
                .status(GroupWalletStatus.ACTIVE)
                .createdAt(now)
                .build();
        wallet = groupWalletRepository.save(wallet);

        GroupWalletRes walletRes = mapToWalletRes(wallet);
        GroupMemberRes memberRes = mapToMemberRes(member);

        return new GroupDetailRes(
                group.getId(),
                group.getName(),
                group.getDescription(),
                group.getStatus(),
                group.getTarget(),
                group.getIsSettlementEnabled(),
                group.getIsSettlementEnabled(),
                group.getIsJoinWithoutConfirm(),
                group.getCreatedAt(),
                GroupRole.OWNER,
                walletRes,
                List.of(walletRes),
                List.of(memberRes)
        );
    }

    @Override
    public List<GroupSummaryRes> list(UUID userId) {
        List<Group> groups = groupRepository.findAllActiveByUserId(userId);
        List<GroupSummaryRes> result = new ArrayList<>();

        for (Group group : groups) {
            long memberCount = groupMemberRepository.countByGroupIdAndStatus(group.getId(), MemberStatus.ACTIVE);
            long walletCount = groupWalletRepository.countByGroupIdAndStatus(group.getId(), GroupWalletStatus.ACTIVE);
            Long totalFunds = groupWalletRepository.sumCurrentBalanceByGroupId(group.getId());
            if (totalFunds == null) {
                totalFunds = 0L;
            }

            GroupMember currentMember = groupMemberRepository.findByGroupIdAndUserIdAndStatusIn(
                    group.getId(), userId, List.of(MemberStatus.ACTIVE)
            ).orElse(null);

            GroupRole myRole = currentMember != null ? currentMember.getRole() : GroupRole.MEMBER;

            result.add(new GroupSummaryRes(
                    group.getId(),
                    group.getName(),
                    myRole,
                    memberCount,
                    walletCount,
                    totalFunds,
                    group.getTarget(),
                    group.getCreatedAt()
            ));
        }

        return result;
    }

    @Override
    public GroupDetailRes detail(UUID userId, UUID groupId) {
        Group group = findActiveGroupAndVerifyMember(groupId, userId);

        GroupMember currentMember = groupMemberRepository.findByGroupIdAndUserIdAndStatusIn(
                groupId, userId, List.of(MemberStatus.ACTIVE)
        ).orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN_NOT_GROUP_MEMBER));

        GroupWallet fund = groupWalletRepository.findFirstByGroupId(groupId).orElse(null);
        GroupWalletRes fundRes = fund != null ? mapToWalletRes(fund) : null;
        List<GroupWalletRes> wallets = fundRes != null ? List.of(fundRes) : List.of();

        List<GroupMemberRes> members = groupMemberRepository.findByGroupIdOrderByJoinedAtDesc(groupId)
                .stream()
                .map(this::mapToMemberRes)
                .toList();

        return new GroupDetailRes(
                group.getId(),
                group.getName(),
                group.getDescription(),
                group.getStatus(),
                group.getTarget(),
                group.getIsSettlementEnabled(),
                group.getIsSettlementEnabled(),
                group.getIsJoinWithoutConfirm(),
                group.getCreatedAt(),
                currentMember.getRole(),
                fundRes,
                wallets,
                members
        );
    }

    @Override
    @Transactional
    public GroupDetailRes update(UUID userId, UUID groupId, GroupUpdateReq req) {
        verifyOwnerRole(groupId, userId);

        Group group = groupRepository.findById(groupId)
                .filter(g -> g.getStatus() != GroupStatus.DELETED)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

        if (group.getStatus() == GroupStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.GROUP_ARCHIVED);
        }

        if (req.name() != null && !req.name().isBlank()) {
            group.setName(req.name().trim());
        }
        if (req.description() != null) {
            group.setDescription(req.description().trim());
        }
        if (req.target() != null) {
            group.setTarget(req.target());
        }
        if (req.isSettlementEnabled() != null) {
            group.setIsSettlementEnabled(req.isSettlementEnabled());
        }
        if (req.isJoinWithoutConfirm() != null) {
            group.setIsJoinWithoutConfirm(req.isJoinWithoutConfirm());
        }
        group.setUpdatedAt(Instant.now());
        groupRepository.save(group);

        return detail(userId, groupId);
    }

    @Override
    @Transactional
    public void archive(UUID userId, UUID groupId) {
        verifyOwnerRole(groupId, userId);

        Group group = groupRepository.findById(groupId)
                .filter(g -> g.getStatus() != GroupStatus.DELETED)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

        long pendingTxnCount = groupTransactionRepository.countByGroupIdAndStatusAndDeletedAtIsNull(
                groupId, GroupTransactionStatus.PENDING
        );
        if (pendingTxnCount > 0) {
            throw new BusinessException(ErrorCode.GROUP_HAS_PENDING_TRANSACTIONS);
        }

        group.setStatus(GroupStatus.ARCHIVED);
        group.setUpdatedAt(Instant.now());
        groupRepository.save(group);
    }

    @Override
    @Transactional
    public void unarchive(UUID userId, UUID groupId) {
        verifyOwnerRole(groupId, userId);

        Group group = groupRepository.findById(groupId)
                .filter(g -> g.getStatus() != GroupStatus.DELETED)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

        group.setStatus(GroupStatus.ACTIVE);
        group.setUpdatedAt(Instant.now());
        groupRepository.save(group);
    }

    @Override
    @Transactional
    public void transferOwnership(UUID userId, UUID groupId, UUID newOwnerUserId) {
        verifyOwnerRole(groupId, userId);

        if (userId.equals(newOwnerUserId)) {
            return;
        }

        GroupMember currentOwner = groupMemberRepository.findByGroupIdAndUserIdAndStatusIn(
                groupId, userId, List.of(MemberStatus.ACTIVE)
        ).orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN_OWNER_REQUIRED));

        GroupMember newOwner = groupMemberRepository.findByGroupIdAndUserIdAndStatusIn(
                groupId, newOwnerUserId, List.of(MemberStatus.ACTIVE)
        ).orElseThrow(() -> new BusinessException(ErrorCode.GROUP_MEMBER_NOT_FOUND));

        currentOwner.setRole(GroupRole.MEMBER);
        newOwner.setRole(GroupRole.OWNER);

        groupMemberRepository.save(currentOwner);
        groupMemberRepository.save(newOwner);
    }

    @Override
    @Transactional
    public void delete(UUID userId, UUID groupId) {
        verifyOwnerRole(groupId, userId);

        Group group = groupRepository.findById(groupId)
                .filter(g -> g.getStatus() != GroupStatus.DELETED)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

        Long totalFunds = groupWalletRepository.sumCurrentBalanceByGroupId(groupId);
        if (totalFunds != null && totalFunds != 0) {
            throw new BusinessException(ErrorCode.CANNOT_DELETE_GROUP_WITH_BALANCE);
        }

        long pendingTxnCount = groupTransactionRepository.countByGroupIdAndStatusAndDeletedAtIsNull(
                groupId, GroupTransactionStatus.PENDING
        );
        if (pendingTxnCount > 0) {
            throw new BusinessException(ErrorCode.GROUP_HAS_PENDING_TRANSACTIONS);
        }

        group.setStatus(GroupStatus.DELETED);
        group.setUpdatedAt(Instant.now());
        groupRepository.save(group);

        // Đóng quỹ duy nhất
        groupWalletRepository.findFirstByGroupId(groupId).ifPresent(w -> {
            w.setStatus(GroupWalletStatus.CLOSED);
            groupWalletRepository.save(w);
        });
    }

    @Override
    @Transactional
    public GroupInviteCodeRes getInviteCode(UUID userId, UUID groupId) {
        verifyOwnerRole(groupId, userId);

        Group group = groupRepository.findById(groupId)
                .filter(g -> g.getStatus() != GroupStatus.DELETED)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

        Instant now = Instant.now();
        boolean isExpired = group.getInviteCodeExpiresAt() != null && group.getInviteCodeExpiresAt().isBefore(now);

        if (group.getInviteCode() == null || isExpired) {
            group.setInviteCode(generateUniqueInviteCode());
            group.setInviteCodeExpiresAt(now.plus(7, ChronoUnit.DAYS));
            group.setUpdatedAt(now);
            groupRepository.save(group);
        }

        return new GroupInviteCodeRes(group.getInviteCode(), group.getInviteCodeExpiresAt());
    }

    @Override
    @Transactional
    public GroupInviteCodeRes regenerateInviteCode(UUID userId, UUID groupId) {
        verifyOwnerRole(groupId, userId);

        Group group = groupRepository.findById(groupId)
                .filter(g -> g.getStatus() != GroupStatus.DELETED)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

        Instant now = Instant.now();
        group.setInviteCode(generateUniqueInviteCode());
        group.setInviteCodeExpiresAt(now.plus(7, ChronoUnit.DAYS));
        group.setUpdatedAt(now);
        groupRepository.save(group);

        return new GroupInviteCodeRes(group.getInviteCode(), group.getInviteCodeExpiresAt());
    }

    @Override
    @Transactional
    public GroupDetailRes join(UUID userId, GroupJoinReq req) {
        Group group = groupRepository.findByInviteCodeAndStatusNot(req.inviteCode().trim(), GroupStatus.DELETED)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITE_CODE_INVALID));

        if (group.getStatus() == GroupStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.GROUP_ARCHIVED);
        }

        // Kiểm tra hạn mã mời
        if (group.getInviteCodeExpiresAt() != null && group.getInviteCodeExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.INVITE_CODE_INVALID);
        }

        // Kiểm tra xem đã là thành viên ACTIVE hoặc PENDING chưa
        boolean alreadyMember = groupMemberRepository.findCurrentMember(group.getId(), userId).isPresent();
        if (alreadyMember) {
            throw new BusinessException(ErrorCode.ALREADY_IN_GROUP);
        }

        Instant now = Instant.now();
        boolean autoApprove = Boolean.TRUE.equals(group.getIsJoinWithoutConfirm());
        MemberStatus memberStatus = autoApprove ? MemberStatus.ACTIVE : MemberStatus.PENDING;
        Instant joinedAt = autoApprove ? now : null;

        GroupMember member = GroupMember.builder()
                .id(UUID.randomUUID())
                .groupId(group.getId())
                .userId(userId)
                .role(GroupRole.MEMBER)
                .status(memberStatus)
                .joinedAt(joinedAt)
                .build();
        groupMemberRepository.save(member);

        return detail(userId, group.getId());
    }

    @Override
    public List<GroupMemberRes> listMembers(UUID userId, UUID groupId) {
        findActiveGroupAndVerifyMember(groupId, userId);
        return groupMemberRepository.findByGroupIdOrderByJoinedAtDesc(groupId)
                .stream()
                .map(this::mapToMemberRes)
                .toList();
    }

    @Override
    @Transactional
    public void approveMember(UUID userId, UUID groupId, UUID memberUserId) {
        verifyOwnerRole(groupId, userId);

        Group group = groupRepository.findById(groupId)
                .filter(g -> g.getStatus() == GroupStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

        GroupMember member = groupMemberRepository.findByGroupIdAndUserIdAndStatusIn(
                groupId, memberUserId, List.of(MemberStatus.PENDING)
        ).orElseThrow(() -> new BusinessException(ErrorCode.GROUP_MEMBER_NOT_FOUND));

        member.setStatus(MemberStatus.ACTIVE);
        member.setJoinedAt(Instant.now());
        groupMemberRepository.save(member);
    }

    @Override
    @Transactional
    public void updateMemberRole(UUID userId, UUID groupId, UUID memberUserId, GroupRole role) {
        verifyOwnerRole(groupId, userId);

        GroupMember member = groupMemberRepository.findByGroupIdAndUserIdAndStatusIn(
                groupId, memberUserId, List.of(MemberStatus.ACTIVE)
        ).orElseThrow(() -> new BusinessException(ErrorCode.GROUP_MEMBER_NOT_FOUND));

        member.setRole(role);
        groupMemberRepository.save(member);
    }

    @Override
    @Transactional
    public void removeMember(UUID userId, UUID groupId, UUID memberUserId) {
        verifyOwnerRole(groupId, userId);

        GroupMember member = groupMemberRepository.findByGroupIdAndUserIdAndStatusIn(
                groupId, memberUserId, List.of(MemberStatus.ACTIVE, MemberStatus.PENDING)
        ).orElseThrow(() -> new BusinessException(ErrorCode.GROUP_MEMBER_NOT_FOUND));

        if (member.getRole() == GroupRole.OWNER) {
            throw new BusinessException(ErrorCode.CANNOT_REMOVE_OWNER);
        }

        long pendingCount = groupTransactionRepository.countByGroupIdAndStatusAndDeletedAtIsNull(
                groupId, GroupTransactionStatus.PENDING
        );
        if (pendingCount > 0) {
            throw new BusinessException(ErrorCode.GROUP_HAS_PENDING_TRANSACTIONS);
        }

        // Bàn giao quỹ về chủ nhóm nếu người bị mời rời là thủ quỹ
        reassignWalletToOwnerIfHeldBy(groupId, memberUserId, userId);

        member.setStatus(MemberStatus.REMOVED);
        member.setLeftAt(Instant.now());
        groupMemberRepository.save(member);
    }

    @Override
    @Transactional
    public void leave(UUID userId, UUID groupId) {
        GroupMember member = groupMemberRepository.findByGroupIdAndUserIdAndStatusIn(
                groupId, userId, List.of(MemberStatus.ACTIVE)
        ).orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN_NOT_GROUP_MEMBER));

        if (member.getRole() == GroupRole.OWNER) {
            long ownerCount = groupMemberRepository.findByGroupIdAndStatus(groupId, MemberStatus.ACTIVE)
                    .stream()
                    .filter(m -> m.getRole() == GroupRole.OWNER)
                    .count();
            if (ownerCount <= 1) {
                throw new BusinessException(ErrorCode.CANNOT_REMOVE_OWNER);
            }
        }

        long pendingCount = groupTransactionRepository.countByGroupIdAndStatusAndDeletedAtIsNull(
                groupId, GroupTransactionStatus.PENDING
        );
        if (pendingCount > 0) {
            throw new BusinessException(ErrorCode.GROUP_HAS_PENDING_TRANSACTIONS);
        }

        // Bàn giao quỹ về chủ nhóm nếu người rời là thủ quỹ
        groupMemberRepository.findByGroupIdAndRoleAndStatus(groupId, GroupRole.OWNER, MemberStatus.ACTIVE)
                .ifPresent(owner -> reassignWalletToOwnerIfHeldBy(groupId, userId, owner.getUserId()));

        member.setStatus(MemberStatus.LEFT);
        member.setLeftAt(Instant.now());
        groupMemberRepository.save(member);
    }

    private void reassignWalletToOwnerIfHeldBy(UUID groupId, UUID targetUserId, UUID ownerUserId) {
        groupWalletRepository.findFirstByGroupId(groupId).ifPresent(wallet -> {
            if (wallet.getHeldByUserId().equals(targetUserId)) {
                wallet.setHeldByUserId(ownerUserId);
                groupWalletRepository.save(wallet);
            }
        });
    }

    private Group findActiveGroupAndVerifyMember(UUID groupId, UUID userId) {
        Group group = groupRepository.findById(groupId)
                .filter(g -> g.getStatus() != GroupStatus.DELETED)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

        boolean isMember = groupMemberRepository.existsByGroupIdAndUserIdAndStatus(
                groupId, userId, MemberStatus.ACTIVE
        );
        if (!isMember) {
            throw new BusinessException(ErrorCode.FORBIDDEN_NOT_GROUP_MEMBER);
        }

        return group;
    }

    private void verifyOwnerRole(UUID groupId, UUID userId) {
        boolean isOwner = groupMemberRepository.existsByGroupIdAndUserIdAndRoleAndStatus(
                groupId, userId, GroupRole.OWNER, MemberStatus.ACTIVE
        );
        if (!isOwner) {
            throw new BusinessException(ErrorCode.FORBIDDEN_OWNER_REQUIRED);
        }
    }

    private String generateUniqueInviteCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(INVITE_CHARS.charAt(RANDOM.nextInt(INVITE_CHARS.length())));
            }
            String code = sb.toString();
            if (groupRepository.findByInviteCodeAndStatusNot(code, GroupStatus.DELETED).isEmpty()) {
                return code;
            }
        }
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private GroupWalletRes mapToWalletRes(GroupWallet wallet) {
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

    private GroupMemberRes mapToMemberRes(GroupMember member) {
        return new GroupMemberRes(
                member.getId(),
                member.getUserId(),
                member.getRole(),
                member.getStatus(),
                member.getJoinedAt()
        );
    }
}

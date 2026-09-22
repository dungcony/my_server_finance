package com.datn.financeapp.group.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.request.group.GroupCreateReq;
import com.datn.financeapp.group.dto.response.group.GroupDetailRes;
import com.datn.financeapp.group.dto.response.group.GroupInviteCodeRes;
import com.datn.financeapp.group.dto.request.group.GroupJoinReq;
import com.datn.financeapp.group.dto.response.group.GroupMemberRes;
import com.datn.financeapp.group.dto.response.group.GroupSummaryRes;
import com.datn.financeapp.group.dto.request.group.GroupUpdateReq;
import com.datn.financeapp.group.dto.response.wallet.GroupWalletRes;
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
import com.datn.financeapp.group.helper.GroupInviteCodeHelper;
import com.datn.financeapp.group.mapper.GroupMapper;
import com.datn.financeapp.group.mapper.GroupMemberMapper;
import com.datn.financeapp.group.mapper.GroupWalletMapper;
import com.datn.financeapp.group.service.GroupMemberService;
import com.datn.financeapp.group.service.GroupService;
import com.datn.financeapp.group.validator.GroupPermissionValidator;
import java.time.Instant;
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

    private final GroupRepository groupRepository;
    private final GroupWalletRepository groupWalletRepository;
    private final GroupTransactionRepository groupTransactionRepository;
    private final GroupWalletMapper groupWalletMapper;
    private final GroupInviteCodeHelper inviteCodeHelper;
    private final GroupMemberService groupMemberService;

    private final GroupMemberRepository groupMemberRepository;
    private final GroupMemberMapper groupMemberMapper;
    private final GroupPermissionValidator permissionValidator;
    private final GroupMapper groupMapper;
    @Override
    public GroupDetailRes findNotDeletedById(UUID id) {
        Group group = groupRepository.findById(id)
                .filter(g -> g.getStatus() != GroupStatus.DELETED)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

        return buildGroupDetailRes(group, null);
    }

    @Override
    @Transactional
    public GroupDetailRes create(UUID userId, GroupCreateReq req) {
        Instant now = Instant.now();
        UUID groupId = UUID.randomUUID();

        Group group = groupMapper.toEntity(
                req,
                groupId,
                inviteCodeHelper.generateUniqueInviteCode(),
                now
        );
        group = groupRepository.save(group);

        var  member = groupMemberService.addMember(
                groupId,
                userId,
                GroupRole.OWNER,
                now
        );

        GroupWallet wallet = GroupWallet.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .heldByUserId(userId)
                .currentBalance(0L)
                .status(GroupWalletStatus.ACTIVE)
                .createdAt(now)
                .build();
        wallet = groupWalletRepository.save(wallet);

        GroupWalletRes walletRes = groupWalletMapper.toResponse(wallet);

        return groupMapper.toDetailResponse(
                group,
                GroupRole.OWNER,
                walletRes,
                List.of(member)
        );
    }

    @Override
    public List<GroupSummaryRes> groupsByUser(UUID userId) {
        List<Group> groups = groupRepository.findAllActiveByUserId(userId);
        List<GroupSummaryRes> result = new ArrayList<>();

        for (Group group : groups) {
            long memberCount = groupMemberService.countActiveMembers(group.getId());
            Long fundBalance = groupWalletRepository.findByGroupId(group.getId())
                    .map(GroupWallet::getCurrentBalance)
                    .orElse(0L);

            var currentMember = groupMemberService.findMemberById(group.getId(), userId);

            GroupRole myRole = currentMember != null ? currentMember.role() : GroupRole.MEMBER;

            result.add(groupMapper.toSummaryResponse(
                    group,
                    myRole,
                    memberCount,
                    fundBalance
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

        return buildGroupDetailRes(group, currentMember.getRole());
    }

    private GroupDetailRes buildGroupDetailRes(Group group, GroupRole role) {
        GroupWallet fund = groupWalletRepository.findByGroupId(group.getId()).orElse(null);
        GroupWalletRes fundRes = fund != null ? groupWalletMapper.toResponse(fund) : null;

        List<GroupMemberRes> members = groupMemberRepository.findByGroupIdOrderByJoinedAtDesc(group.getId())
                .stream()
                .map(groupMemberMapper::toResponse)
                .toList();

        return groupMapper.toDetailResponse(group, role, fundRes, members);
    }

    @Override
    @Transactional
    public GroupDetailRes update(UUID userId, UUID groupId, GroupUpdateReq req) {
        permissionValidator.verifyOwnerRole(groupId, userId);
        Group group = permissionValidator.validateAndGetActiveGroup(groupId);

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
        permissionValidator.verifyOwnerRole(groupId, userId);
        Group group = permissionValidator.validateAndGetGroup(groupId);

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
        permissionValidator.verifyOwnerRole(groupId, userId);
        Group group = permissionValidator.validateAndGetGroup(groupId);

        group.setStatus(GroupStatus.ACTIVE);
        group.setUpdatedAt(Instant.now());
        groupRepository.save(group);
    }

    @Override
    @Transactional
    public void transferOwnership(UUID userId, UUID groupId, UUID newOwnerUserId) {
        permissionValidator.verifyOwnerRole(groupId, userId);

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
        permissionValidator.verifyOwnerRole(groupId, userId);
        Group group = permissionValidator.validateAndGetGroup(groupId);

        GroupWallet wallet = groupWalletRepository.findByGroupId(groupId).orElse(null);
        if (wallet != null && wallet.getCurrentBalance() != 0) {
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
        if (wallet != null) {
            wallet.setStatus(GroupWalletStatus.CLOSED);
            groupWalletRepository.save(wallet);
        }
    }

    @Override
    @Transactional
    public GroupInviteCodeRes getInviteCode(UUID userId, UUID groupId) {
        permissionValidator.verifyOwnerRole(groupId, userId);
        Group group = permissionValidator.validateAndGetGroup(groupId);

        if (group.getInviteCode() == null || group.getInviteCode().isBlank()) {
            group.setInviteCode(inviteCodeHelper.generateUniqueInviteCode());
            group.setUpdatedAt(Instant.now());
            groupRepository.save(group);
        }

        return new GroupInviteCodeRes(group.getInviteCode());
    }

    @Override
    @Transactional
    public GroupInviteCodeRes regenerateInviteCode(UUID userId, UUID groupId) {
        permissionValidator.verifyOwnerRole(groupId, userId);
        Group group = permissionValidator.validateAndGetGroup(groupId);

        Instant now = Instant.now();
        group.setInviteCode(inviteCodeHelper.generateUniqueInviteCode());
        group.setUpdatedAt(now);
        groupRepository.save(group);

        return new GroupInviteCodeRes(group.getInviteCode());
    }

    @Override
    @Transactional
    public GroupDetailRes join(UUID userId, GroupJoinReq req) {
        Group group = groupRepository.findByInviteCodeAndStatusNot(req.inviteCode().trim(), GroupStatus.DELETED)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITE_CODE_INVALID));

        if (group.getStatus() == GroupStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.GROUP_ARCHIVED);
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
                .map(groupMemberMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void approveMember(UUID userId, UUID groupId, UUID memberUserId) {

        permissionValidator.verifyOwnerRole(groupId, userId);

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
        permissionValidator.verifyOwnerRole(groupId, userId);

        GroupMember member = groupMemberRepository.findByGroupIdAndUserIdAndStatusIn(
                groupId, memberUserId, List.of(MemberStatus.ACTIVE)
        ).orElseThrow(() -> new BusinessException(ErrorCode.GROUP_MEMBER_NOT_FOUND));

        member.setRole(role);
        groupMemberRepository.save(member);
    }

    @Override
    @Transactional
    public void removeMember(UUID userId, UUID groupId, UUID memberUserId) {
        permissionValidator.verifyOwnerRole(groupId, userId);

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
        Group group = permissionValidator.validateAndGetGroup(groupId);
        permissionValidator.verifyActiveMember(groupId, userId);
        return group;
    }
}

package com.datn.financeapp.group.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.request.group.GroupCreateReq;
import com.datn.financeapp.group.dto.request.group.GroupJoinReq;
import com.datn.financeapp.group.dto.request.group.GroupUpdateReq;
import com.datn.financeapp.group.dto.request.member.MemberCreateReq;
import com.datn.financeapp.group.dto.response.fund.FundRes;
import com.datn.financeapp.group.dto.response.group.GroupDetailRes;
import com.datn.financeapp.group.dto.response.group.GroupPendingCountRes;
import com.datn.financeapp.group.dto.response.group.GroupSummaryRes;
import com.datn.financeapp.group.dto.response.member.MemberRes;
import com.datn.financeapp.group.entity.Group;
import com.datn.financeapp.group.enums.GroupStatus;
import com.datn.financeapp.group.enums.MemberRole;
import com.datn.financeapp.group.enums.MemberStatus;
import com.datn.financeapp.group.helper.MemberAuthInfo;
import com.datn.financeapp.group.helper.MemberViewEnricher;
import com.datn.financeapp.group.mapper.FundMapper;
import com.datn.financeapp.group.mapper.GroupMapper;
import com.datn.financeapp.group.repository.GroupRepository;
import com.datn.financeapp.group.service.FundService;
import com.datn.financeapp.group.service.GTransactionService;
import com.datn.financeapp.group.service.GroupService;
import com.datn.financeapp.group.service.MemberService;
import com.datn.financeapp.group.validator.GroupPermissionValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupServiceImpl implements GroupService {

    private final GroupRepository groupRepository;
    private final GTransactionService gTransactionService;
    private final MemberService memberService;
    private final FundService fundService;
    private final GroupPermissionValidator permissionValidator;
    private final GroupMapper groupMapper;
    private final FundMapper fundMapper;
    private final MemberViewEnricher memberViewEnricher;


    private static final int inviteCodeLength = 8;

    @Override
    public GroupDetailRes findNotDeletedById(UUID groupId) {
        Group group = groupRepository.findNotDeletedWithFundById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

        FundRes fund = (group.getFund() != null)
                ? fundMapper.toResponse(group.getFund())
                : null;

        return groupMapper.toDetailResponse(group, null, fund, List.of());
    }

    @Override
    @Transactional
    public GroupDetailRes create(UUID operatorId, GroupCreateReq req) {

        if (req.members().contains(operatorId))
            throw new BusinessException(ErrorCode.GROUP_CREATE_MEMBER_CAN_NOT_OWNER);

        if (req.members().isEmpty())
            throw new BusinessException(ErrorCode.GROUP_CREATE_NOT_ONLY_ONE);

        Instant now = Instant.now();
        UUID groupId = UUID.randomUUID();

        Group group = groupMapper.toEntity(req, groupId, generateUniqueInviteCode(inviteCodeLength), now);

        group = groupRepository.save(group);

        var fund = fundService.create(groupId, operatorId, now);

        var own = memberService.create(
                new MemberCreateReq(
                        groupId,
                        operatorId,
                        MemberRole.OWNER,
                        MemberStatus.ACTIVE
                )
        ).orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));

        List<UUID> memberIds = req.members().stream()
                .filter(id -> id != null && !id.equals(operatorId))
                .distinct()
                .toList();

        List<MemberCreateReq> memberCreateReqs = new ArrayList<>();
        for (UUID memberId : memberIds) {
            MemberCreateReq mem = new MemberCreateReq(
                    groupId,
                    memberId,
                    MemberRole.MEMBER,
                    MemberStatus.ACTIVE
            );
            memberCreateReqs.add(mem);
        }

        var members = memberService.creates(memberCreateReqs);

        List<MemberRes> allMembers = new ArrayList<>();
        allMembers.add(own);
        allMembers.addAll(members);
        allMembers = memberViewEnricher.enrich(allMembers, fund.keepperId());

        return groupMapper.toDetailResponse(
                group,
                MemberRole.OWNER,
                fund,
                allMembers
        );
    }

    @Override
    public List<GroupSummaryRes> list(UUID operatorId) {
        return groupRepository.findSummariesByUserId(operatorId);
    }

    @Override
    public GroupDetailRes detail(UUID operatorId, UUID groupId) {

        MemberRes mem = memberService.getMember(groupId, operatorId, MemberStatus.ACTIVE);

        Group group = groupRepository.findNotDeletedWithFundById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

        return buildGroupDetailRes(group, operatorId, mem.role());
    }

    @Override
    @Transactional
    public GroupDetailRes update(UUID operatorId, UUID groupId, GroupUpdateReq req) {
        MemberAuthInfo memberRole = permissionValidator.getAuthInfo(groupId, operatorId);

        Group group = groupRepository.findNotDeletedWithFundById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

        if (req.name() != null && !req.name().isBlank())
            group.setName(req.name().trim());

        if (req.description() != null)
            group.setDescription(req.description().trim());

        if (req.target() != null)
            group.setTarget(req.target());

        if (req.isSettlementEnabled() != null)
            group.setIsSettlementEnabled(req.isSettlementEnabled());

        if (req.isJoinWithoutConfirm() != null)
            group.setIsJoinWithoutConfirm(req.isJoinWithoutConfirm());

        group.setUpdatedAt(Instant.now());
        groupRepository.save(group);

        return buildGroupDetailRes(group, operatorId, memberRole.memberRole());
    }

    @Override
    @Transactional
    public void archive(UUID operatorId, UUID groupId) {
        permissionValidator.verifyOwner(groupId, operatorId);
        Group group = groupRepository.findActivatedWithFundById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

        if (gTransactionService.countPendingForGroup(groupId) > 0)
            throw new BusinessException(ErrorCode.GROUP_HAS_PENDING_TRANSACTIONS);

        group.setStatus(GroupStatus.ARCHIVED);
        group.setUpdatedAt(Instant.now());
        groupRepository.save(group);
        log.info("nhóm đã được lưu trữ");
    }

    @Override
    @Transactional
    public void unarchive(UUID operatorId, UUID groupId) {

        permissionValidator.verifyOwnerAllowArchived(groupId, operatorId);

        Group group = groupRepository.findArchivedWithFundById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_ARCHIVED));

        group.setStatus(GroupStatus.ACTIVE);
        group.setUpdatedAt(Instant.now());
        groupRepository.save(group);

        log.info("nhóm đã được hoạt động trở lại");
    }

    @Override
    @Transactional
    public void delete(UUID operatorId, UUID groupId) {
        permissionValidator.getAuthInfo(groupId, operatorId);
        Group group = groupRepository.findNotDeletedWithFundById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

        if (group.getFund().getCurrentBalance() != null && group.getFund().getCurrentBalance() != 0)
            throw new BusinessException(ErrorCode.CANNOT_DELETE_GROUP_WITH_BALANCE);

        if (gTransactionService.countPendingForGroup(groupId) > 0)
            throw new BusinessException(ErrorCode.GROUP_HAS_PENDING_TRANSACTIONS);


        group.setStatus(GroupStatus.DELETED);
        group.setUpdatedAt(Instant.now());
        groupRepository.save(group);

        log.info("deleted {} is completed", group.getId());
    }

    @Override
    @Transactional
    public void joinByCode(UUID operatorId, GroupJoinReq req) {
        Group group = groupRepository.findByInviteCodeAndStatusNot(req.inviteCode(), GroupStatus.DELETED)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

        if (group.getStatus().equals(GroupStatus.ARCHIVED))
            throw new BusinessException(ErrorCode.GROUP_ARCHIVED);

        memberService.assertNotInGroup(group.getId(), operatorId);

        MemberStatus status = MemberStatus.PENDING;

        if (Boolean.TRUE.equals(group.getIsJoinWithoutConfirm()))
            status = MemberStatus.ACTIVE;


        try {
            memberService.create(
                    new MemberCreateReq(
                            group.getId(),
                            operatorId,
                            MemberRole.MEMBER,
                            status
                    )
            );
            log.info("Successfully joined the group.");
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.ALREADY_IN_GROUP);
        }

    }

    @Override
    public GroupPendingCountRes pendingCount(UUID operatorId, UUID groupId) {
        MemberAuthInfo info = permissionValidator.getAuthInfo(groupId, operatorId);

        // chỉ người có quyền duyệt mới thấy số việc đang chờ
        long pendingTransactions = (info.isOwner() || info.isTreasurer())
                ? gTransactionService.countPendingForGroup(groupId)
                : 0L;
        long pendingMembers = info.isOwner()
                ? memberService.countPendingMembers(groupId)
                : 0L;

        return new GroupPendingCountRes(pendingTransactions, pendingMembers);
    }

    //----------------------------------------PRIVATE------------------------------------------//

    private GroupDetailRes buildGroupDetailRes(Group group, UUID operatorId, MemberRole role) {
        List<MemberRes> members = memberService.getActivateMembers(group.getId());

        MemberRes currentMember = members.stream()
                .filter(m -> m.userId().equals(operatorId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN_NOT_GROUP_MEMBER));

        FundRes fund = (group.getFund() != null)
                ? fundMapper.toResponse(group.getFund())
                : null;

        UUID keeperId = fund != null ? fund.keepperId() : null;
        members = memberViewEnricher.enrich(members, keeperId);

        return groupMapper.toDetailResponse(group, currentMember.role(), fund, members);
    }


    /**
     * Sinh chuỗi mã mời ngẫu nhiên gồm {@param length} ký tự.
     */
    public static String generateUniqueInviteCode(int length) {

        String INVITE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        SecureRandom RANDOM = new SecureRandom();

        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(INVITE_CHARS.charAt(RANDOM.nextInt(INVITE_CHARS.length())));
        }
        return sb.toString();

    }

}

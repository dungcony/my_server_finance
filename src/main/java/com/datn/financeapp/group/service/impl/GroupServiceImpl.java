package com.datn.financeapp.group.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.request.group.GroupCreateReq;
import com.datn.financeapp.group.dto.request.group.GroupJoinReq;
import com.datn.financeapp.group.dto.request.group.GroupUpdateReq;
import com.datn.financeapp.group.dto.request.member.MemberAddReq;
import com.datn.financeapp.group.dto.response.fund.GroupFundRes;
import com.datn.financeapp.group.dto.response.group.GroupDetailRes;
import com.datn.financeapp.group.dto.response.group.GroupSummaryRes;
import com.datn.financeapp.group.dto.response.member.MemberRes;
import com.datn.financeapp.group.entity.Group;
import com.datn.financeapp.group.enums.GroupStatus;
import com.datn.financeapp.group.enums.MemberRole;
import com.datn.financeapp.group.enums.MemberStatus;
import com.datn.financeapp.group.helper.MemberAuthInfo;
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


    private static final int inviteCodeLength = 8;

    @Override
    public GroupDetailRes findNotDeletedById(UUID groupId) {
        Group group = groupRepository.findNotDeletedWithFundById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

        GroupFundRes fundRes = (group.getFund() != null)
                ? fundMapper.toResponse(group.getFund())
                : null;
        List<MemberRes> members = memberService.findMembers(groupId);

        return groupMapper.toDetailResponse(group, null, fundRes, members);
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

        // 1. Lưu thông tin Nhóm
        Group group = groupMapper.toEntity(req, groupId, generateUniqueInviteCode(inviteCodeLength), now);
        group = groupRepository.save(group);

        // 2. Tạo Quỹ cho nhóm qua Service chuyên trách (Đảm bảo lưu đúng vào DB và nhận về DTO chuẩn)
        var fundRes = fundService.addFund(groupId, operatorId, now);

        // 3. Thêm chủ nhóm (OWNER)
        var own = memberService.addOwner(operatorId, groupId, now);

        // 4. Lọc bỏ operatorId của OWNER khỏi danh sách mời (tránh trùng)
        List<UUID> memberIds = req.members().stream()
                .filter(id -> id != null && !id.equals(operatorId))
                .distinct()
                .toList();

        var members = memberService.addMembers(operatorId, groupId, new MemberAddReq(memberIds, MemberStatus.ACTIVE, MemberRole.MEMBER, now));

        // 5. Ghép OWNER lên đầu danh sách thành viên trả về
        List<MemberRes> allMembers = new ArrayList<>();
        allMembers.add(own);
        allMembers.addAll(members);

        return groupMapper.toDetailResponse(
                group,
                MemberRole.OWNER,
                fundRes,
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
        MemberAuthInfo memberRole = permissionValidator.verifyOwnerInGroupActive(groupId, operatorId);

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
        permissionValidator.verifyOwnerInGroupActive(groupId, operatorId);
        Group group = groupRepository.findActivatedWithFundById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

        if (gTransactionService.countPendingForGroup(groupId) > 0)
            throw new BusinessException(ErrorCode.GROUP_HAS_PENDING_TRANSACTIONS);

        group.setStatus(GroupStatus.ARCHIVED);
        group.setUpdatedAt(Instant.now());
        groupRepository.save(group);
    }

    @Override
    @Transactional
    public void unarchive(UUID operatorId, UUID groupId) {

        permissionValidator.verifyOwner(groupId, operatorId);

        Group group = groupRepository.findArchivedWithFundById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_ARCHIVED));

        group.setStatus(GroupStatus.ACTIVE);
        group.setUpdatedAt(Instant.now());
        groupRepository.save(group);
    }

    @Override
    @Transactional
    public void delete(UUID operatorId, UUID groupId) {
        permissionValidator.verifyOwnerInGroupActive(groupId, operatorId);
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

        Instant now = null;

        Group group = groupRepository.findByInviteCodeAndStatusNot(req.inviteCode().trim(), GroupStatus.DELETED)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITE_CODE_INVALID));

        if (group.getStatus() == GroupStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.GROUP_ARCHIVED);
        }

        MemberStatus status = MemberStatus.PENDING;

        if (group.getIsJoinWithoutConfirm() == Boolean.TRUE) {
            status = MemberStatus.ACTIVE;
            now = Instant.now();
        }

        memberService.addMember(
                operatorId,
                group.getId(),
                status,
                now
        );

        log.info("Join group {} with status {}", group.getId(), status);

    }

    private GroupDetailRes buildGroupDetailRes(Group group, UUID operatorId, MemberRole role) {
        List<MemberRes> members;
        if (role == MemberRole.MEMBER)
            members = memberService.findMembers(group.getId(), MemberStatus.ACTIVE);
        else
            members = memberService.findMembers(group.getId());

        MemberRes currentMember = members.stream()
                .filter(m -> m.userId().equals(operatorId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN_NOT_GROUP_MEMBER));

        GroupFundRes fund = (group.getFund() != null)
                ? fundMapper.toResponse(group.getFund())
                : null;

        return groupMapper.toDetailResponse(group, currentMember.role(), fund, members);
    }

    //----------------------------------------PRIVATE------------------------------------------//


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

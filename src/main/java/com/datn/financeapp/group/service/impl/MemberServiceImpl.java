package com.datn.financeapp.group.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.request.member.MemberAddReq;
import com.datn.financeapp.group.dto.response.member.MemberRes;
import com.datn.financeapp.group.entity.Member;
import com.datn.financeapp.group.enums.MemberRole;
import com.datn.financeapp.group.enums.MemberStatus;
import com.datn.financeapp.group.mapper.MemberMapper;
import com.datn.financeapp.group.repository.MemberRepository;
import com.datn.financeapp.group.service.MemberService;
import com.datn.financeapp.group.validator.GroupPermissionValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberServiceImpl implements MemberService {

    // trạng thái hiển thị mặc định khi không lọc, bỏ qua LEFT và REMOVED
    private static final List<MemberStatus> VISIBLE_STATUSES = List.of(MemberStatus.ACTIVE, MemberStatus.PENDING);

    private final MemberRepository memberRepository;
    private final MemberMapper memberMapper;
    private final GroupPermissionValidator permissionValidator;

    @Override
    @Transactional
    public MemberRes addOwner(UUID memberId, UUID groupId, Instant now) {
        Instant joinedAt = now != null ? now : Instant.now();
        Member member = buildGroupMember(groupId, memberId, joinedAt);
        member = memberRepository.save(member);

        log.info("thêm trưởng nhóm thành công");
        return memberMapper
                .toResponse(member);
    }

    @Override
    @Transactional
    public MemberRes addMember(UUID memberId, UUID groupId, MemberStatus status, Instant now) {
        return addMembers(null, groupId, new MemberAddReq(List.of(memberId), status, MemberRole.MEMBER, now)).get(0);
    }

    @Override
    @Transactional
    public List<MemberRes> addMembers(UUID operatorId, UUID groupId, MemberAddReq req) {
        List<UUID> memberIds = req.memberIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (memberIds.isEmpty())
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);

        // operatorId null là tự vào nhóm, còn lại phải là chủ nhóm
        if (operatorId != null)
            permissionValidator.verifyOwnerInGroupActive(groupId, operatorId);

        MemberRole role = req.role() != null ? req.role() : MemberRole.MEMBER;

        assertNoneInGroup(groupId, memberIds);

        List<Member> members = memberIds.stream()
                .map(uid -> buildGroupMember(groupId, uid, role, req.status(), req.addAt()))
                .toList();

        log.info("thêm {} thành viên vào nhóm {}", members.size(), groupId);
        return toResponses(memberRepository.saveAll(members));
    }


    @Override
    public long countActiveMembers(UUID groupId) {
        return memberRepository.countByGroupIdAndStatus(groupId, MemberStatus.ACTIVE);
    }

    @Override
    public MemberRes getMember(UUID groupId, UUID memberId, MemberStatus status) {

        if (status == null)
            return memberRepository.findByGroupIdAndUserId(groupId, memberId)
                    .map(memberMapper::toResponse)
                    .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN_NOT_GROUP_MEMBER));

        return memberRepository.findByGroupIdAndUserIdAndStatus(groupId, memberId, status)
                .map(memberMapper::toResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN_NOT_GROUP_MEMBER));
    }

    @Override
    public List<MemberRes> findMembers(UUID groupId, List<UUID> memberIds, MemberStatus memberStatus) {

        if (groupId == null)
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);

        // không lọc theo id thì chỉ lọc theo trạng thái, không truyền trạng thái thì chỉ lấy thành viên ACTIVE và PENDING
        if (memberIds == null) {
            List<Member> members = memberStatus == null
                    ? memberRepository.findByGroupIdAndStatusInOrderByJoinedAtDesc(groupId, VISIBLE_STATUSES)
                    : memberRepository.findByGroupIdAndStatusOrderByJoinedAtDesc(groupId, memberStatus);
            return toResponses(members);
        }

        // danh sách id rỗng thì không có ai để tìm, tránh câu IN rỗng
        if (memberIds.isEmpty())
            return List.of();

        // id null hoặc trùng lặp là dữ liệu đầu vào sai
        if (memberIds.stream().filter(Objects::nonNull).distinct().count() != memberIds.size())
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);

        List<Member> members = memberStatus == null
                ? memberRepository.findByGroupIdAndUserIdInAndStatusIn(groupId, memberIds, VISIBLE_STATUSES)
                : memberRepository.findByGroupIdAndUserIdInAndStatus(groupId, memberIds, memberStatus);
        return toResponses(members);
    }


    @Override
    public List<UUID> findIdAllMember(UUID groupId) {
        return memberRepository.findByGroupIdAndStatusOrderByJoinedAtDesc(groupId, MemberStatus.ACTIVE)
                .stream()
                .map(Member::getUserId)
                .toList();
    }

    @Override
    public boolean allMemberInGroup(UUID groupId, List<UUID> memberIds) {
        return memberRepository.allMemberInGroup(groupId, memberIds);
    }


    // --------------------------------------------- PRIVATE--------------------------------------------//
    private Member buildGroupMember(UUID groupId, UUID userId, Instant joinedAt) {
        return buildGroupMember(groupId, userId, MemberRole.OWNER, MemberStatus.ACTIVE, joinedAt);
    }

    private Member buildGroupMember(UUID groupId, UUID userId, MemberRole role, MemberStatus status, Instant joinedAt) {
        return Member.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .userId(userId)
                .role(role)
                .status(status)
                .joinedAt(joinedAt)
                .build();
    }

    // chặn người đã ACTIVE hoặc đang PENDING
    private void assertNoneInGroup(UUID groupId, List<UUID> userIds) {
        memberRepository.findByGroupIdAndUserIdInAndStatusIn(groupId, userIds, VISIBLE_STATUSES)
                .stream()
                .findFirst()
                .ifPresent(m -> {
                    throw new BusinessException(m.getStatus() == MemberStatus.PENDING
                            ? ErrorCode.PENDING_IN_GROUP
                            : ErrorCode.ALREADY_IN_GROUP);
                });
    }

    private List<MemberRes> toResponses(List<Member> members) {
        return members.stream()
                .map(memberMapper::toResponse)
                .toList();
    }

}

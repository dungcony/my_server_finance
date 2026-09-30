package com.datn.financeapp.group.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.response.member.MemberRes;
import com.datn.financeapp.group.entity.Member;
import com.datn.financeapp.group.enums.MemberRole;
import com.datn.financeapp.group.enums.MemberStatus;
import com.datn.financeapp.group.events.MemberLeaveEvent;
import com.datn.financeapp.group.mapper.MemberMapper;
import com.datn.financeapp.group.repository.MemberRepository;
import com.datn.financeapp.group.service.MemberService;
import com.datn.financeapp.group.validator.GroupPermissionValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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

    private final MemberRepository memberRepository;
    private final MemberMapper memberMapper;
    private final GroupPermissionValidator permissionValidator;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    @Transactional
    public MemberRes addOwner(UUID groupId, UUID memberId, Instant now) {
        Instant joinedAt = now != null ? now : Instant.now();
        Member member = buildGroupMember(groupId, memberId, MemberRole.OWNER, joinedAt);
        member = memberRepository.save(member);

        log.info("thêm trưởng nhóm thành công");
        return memberMapper
                .toResponse(member);
    }

    @Override
    @Transactional
    public MemberRes addMember(UUID memberId, UUID groupId, MemberStatus status, Instant now) {

        // Kiểm tra xem đã ở trong nhóm (ACTIVE hoặc PENDING) chưa
        Member member = memberRepository.findByGroupIdAndUserIdAndStatusIn(
                groupId,
                memberId,
                List.of(MemberStatus.ACTIVE, MemberStatus.PENDING)).orElse(null);

        if (member != null) {
            if (member.getStatus() == MemberStatus.PENDING)
                throw new BusinessException(ErrorCode.PENDING_IN_GROUP);
            else if ((member.getStatus() == MemberStatus.ACTIVE))
                throw new BusinessException(ErrorCode.ALREADY_IN_GROUP);
        }

        member = Member.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .status(status)
                .userId(memberId)
                .role(MemberRole.MEMBER)
                .joinedAt(now)
                .build();

        member = memberRepository.save(member);

        log.info("thêm thành viên thành công");

        return memberMapper
                .toResponse(member);
    }

    @Override
    @Transactional
    public List<MemberRes> addMembers(UUID operatorId, UUID groupId, List<UUID> memberIds, Instant now) {

        if (memberIds == null || memberIds.isEmpty())
            return List.of();

        permissionValidator.verifyOwnerInGroupActive(groupId, operatorId);

        Instant joinedAt = now != null ? now : Instant.now();

        List<Member> members = memberIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(uid -> buildGroupMember(groupId, uid, MemberRole.MEMBER, joinedAt))
                .toList();

        if (members.isEmpty())
            return List.of();

        members = memberRepository.saveAll(members);

        log.info("thêm các thành viên thành công");

        return members.stream()
                .map(memberMapper::toResponse)
                .toList();
    }

    @Override
    public long countActiveMembers(UUID groupId) {
        return memberRepository.countByGroupIdAndStatus(groupId, MemberStatus.ACTIVE);
    }

    @Override
    public MemberRes findMember(UUID groupId, UUID memberId) {
        return memberRepository.findByGroupIdAndUserId(groupId, memberId)
                .map(memberMapper::toResponse)
                .orElse(null);
    }

    @Override
    public MemberRes findMember(UUID groupId, UUID memberId, MemberStatus status) {
        return memberRepository.findByGroupIdAndUserIdAndStatus(groupId, memberId, status)
                .map(memberMapper::toResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN_NOT_GROUP_MEMBER));
    }

    @Override
    public List<Member> findMembers(UUID groupId, List<UUID> memberIds) {

        // 1. Kiểm tra an toàn đầu vào (Defensive check để tránh lỗi SQL IN rỗng)
        if (groupId == null || memberIds == null || memberIds.isEmpty())
            return List.of();

        // 2. Lọc bỏ null và các ID trùng lặp (nếu có)
        List<UUID> distinctUserIds = memberIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (distinctUserIds.isEmpty())
            return List.of();

        // 3. Query các thành viên đang ACTIVE của nhóm
        return memberRepository.findByGroupIdAndUserIdInAndStatus(
                groupId,
                distinctUserIds,
                MemberStatus.ACTIVE);
    }

    @Override
    public List<MemberRes> findMembers(UUID groupId) {
        return memberRepository.findByGroupIdAndStatusNotOrderByJoinedAtDesc(groupId, MemberStatus.REMOVED)
                .stream()
                .map(memberMapper::toResponse)
                .toList();
    }

    @Override
    public List<MemberRes> findMembers(UUID groupId, MemberStatus memberStatus) {
        return memberRepository.findByGroupIdAndStatusOrderByJoinedAtDesc(groupId, memberStatus)
                .stream()
                .map(memberMapper::toResponse)
                .toList();
    }

    @Override
    public List<UUID> findIdAllMember(UUID groupId) {
        return memberRepository.findByGroupIdAndStatusOrderByJoinedAtDesc(groupId, MemberStatus.ACTIVE)
                .stream()
                .map(member -> member.getUserId())
                .toList();
    }

    @Override
    public boolean allMemberInGroup(UUID groupId, List<UUID> memberIds) {
        return memberRepository.allMemberInGroup(groupId, memberIds);
    }

    @Override
    @Transactional
    public void leave(UUID operatorId, UUID groupId) {
        Member member = memberRepository.findByGroupIdAndUserIdAndStatus(groupId, operatorId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN_NOT_GROUP_MEMBER));

        if (member.getRole() == MemberRole.OWNER)
            throw new BusinessException(ErrorCode.OWNER_MUST_TRANSFER_FIRST);

        Member owner = findOwner(groupId);

        // bắn sự kiện có thành viên rời nhóm
        applicationEventPublisher.publishEvent(new MemberLeaveEvent(groupId, operatorId, owner.getUserId()));

        member.setStatus(MemberStatus.LEFT);
        member.setLeftAt(Instant.now());
        member = memberRepository.save(member);
    }

    @Transactional
    @Override
    public void approve(UUID operatorId, UUID groupId, UUID memberId) {
        permissionValidator.verifyOwnerInGroupActive(groupId, operatorId);

        // Chạy thẳng 1 câu UPDATE, không cần SELECT trước
        int rowsUpdated = memberRepository.updateStatusAndJoinedAt(
                groupId,
                memberId,
                MemberStatus.PENDING,
                MemberStatus.ACTIVE,
                Instant.now());

        // Nếu rowsUpdated == 0 tức là không có thành viên nào thỏa mãn (không tồn tại
        // hoặc không ở PENDING)
        if (rowsUpdated == 0) {
            throw new BusinessException(ErrorCode.GROUP_MEMBER_NOT_FOUND);
        }
    }

    @Transactional
    @Override
    public int approveAll(UUID operatorId, UUID groupId) {
        // 1. Kiểm tra quyền Owner và nhóm phải đang ACTIVE
        permissionValidator.verifyOwnerInGroupActive(groupId, operatorId);

        // 2. Chạy đúng 1 câu UPDATE toàn bộ những người PENDING -> ACTIVE
        int approvedCount = memberRepository.approvePending(
                groupId,
                Instant.now());

        log.info("Chủ nhóm {} đã duyệt toàn bộ {} thành viên chờ vào nhóm {}",
                operatorId, approvedCount, groupId);

        return approvedCount; // Trả về số lượng người đã được duyệt để FE hiển thị thông báo
    }

    @Override
    @Transactional
    public void removeMember(UUID operatorId, UUID groupId, UUID memberId) {

        if (Objects.equals(operatorId, memberId)) {
            log.info("không thể tự xóa chính mình");
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        permissionValidator.verifyOwnerInGroupActive(groupId, operatorId);

        Member member = memberRepository.findByGroupIdAndUserIdAndStatusIn(
                groupId,
                memberId,
                List.of(MemberStatus.ACTIVE, MemberStatus.PENDING))
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_MEMBER_NOT_FOUND));

        // bắn sự kiện có thành viên rời nhóm
        applicationEventPublisher.publishEvent(new MemberLeaveEvent(groupId, memberId, operatorId));

        member.setStatus(MemberStatus.REMOVED);
        member.setLeftAt(Instant.now());
        member = memberRepository.save(member);
    }

    // --------------------------------------------- PRIVATE
    // --------------------------------------------//
    private Member buildGroupMember(UUID groupId, UUID userId, MemberRole role, Instant joinedAt) {
        return Member.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .userId(userId)
                .role(role)
                .status(MemberStatus.ACTIVE)
                .joinedAt(joinedAt)
                .build();
    }

    private Member findOwner(UUID groupId) {
        return memberRepository.findByGroupIdAndRoleAndStatus(groupId, MemberRole.OWNER, MemberStatus.ACTIVE)
                .orElseThrow(() -> {
                    log.error("Data corruption: Group {} exists but has no ACTIVE OWNER!", groupId);
                    return new BusinessException(ErrorCode.INTERNAL_ERROR);
                });
    }

}

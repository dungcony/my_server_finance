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
import com.datn.financeapp.group.service.MemberBehavierService;
import com.datn.financeapp.group.validator.GroupPermissionValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MemberBehavierServiceImpl implements MemberBehavierService {

    private final MemberRepository memberRepository;
    private final MemberMapper memberMapper;
    private final GroupPermissionValidator permissionValidator;
    private final ApplicationEventPublisher applicationEventPublisher;


    @Override
    public void leave(UUID operatorId, UUID groupId) {
        Member member = memberRepository.findByGroupIdAndUserIdAndStatus(groupId, operatorId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN_NOT_GROUP_MEMBER));

        if (member.getRole() == MemberRole.OWNER)
            throw new BusinessException(ErrorCode.OWNER_MUST_TRANSFER_FIRST);

        // tìm kiếm owner id để bắn sự kiện rời nhóm
        MemberRes owner = findOwner(groupId);

        // bắn sự kiện có thành viên rời nhóm
        applicationEventPublisher.publishEvent(new MemberLeaveEvent(groupId, operatorId, owner.userId()));

        member.setStatus(MemberStatus.LEFT);
        member.setLeftAt(Instant.now());
        memberRepository.save(member);
    }

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

    // MemberBehavierServiceImpl
    @Override
    public void reject(UUID operatorId, UUID groupId, UUID memberId) {
        permissionValidator.verifyOwnerInGroupActive(groupId, operatorId);

        // không có dòng PENDING nào khớp thì báo không tìm thấy
        if (memberRepository.deletePending(groupId, memberId) == 0)
            throw new BusinessException(ErrorCode.GROUP_MEMBER_NOT_FOUND);
    }

    @Override
    public int rejectAll(UUID operatorId, UUID groupId) {
        // người gọi phải là chủ nhóm, nhóm phải đang ACTIVE
        permissionValidator.verifyOwnerInGroupActive(groupId, operatorId);

        // một câu DELETE xóa toàn bộ người đang PENDING
        int rejectedCount = memberRepository.deleteAllPending(groupId);

        log.info("Chủ nhóm {} đã từ chối toàn bộ {} thành viên chờ vào nhóm {}",
                operatorId, rejectedCount, groupId);

        return rejectedCount;
    }

    @Override
    public void removeMember(UUID operatorId, UUID groupId, UUID memberId) {

        if (Objects.equals(operatorId, memberId)) {
            log.info("không thể tự xóa chính mình");
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        permissionValidator.verifyOwnerInGroupActive(groupId, operatorId);

        Member member = memberRepository.findByGroupIdAndUserIdAndStatus(
                        groupId,
                        memberId,
                        MemberStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_MEMBER_NOT_FOUND));

        // bắn sự kiện có thành viên rời nhóm
        applicationEventPublisher.publishEvent(new MemberLeaveEvent(groupId, memberId, operatorId));

        member.setStatus(MemberStatus.REMOVED);
        member.setLeftAt(Instant.now());
        memberRepository.save(member);
    }

    @Override
    public void transferOwnership(UUID operatorId, UUID groupId, UUID memberId) {
        // không thể chuyển quyền cho chính mình
        if (Objects.equals(operatorId, memberId))
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);

        // người gọi phải là chủ nhóm, nhóm phải đang ACTIVE
        permissionValidator.verifyOwnerInGroupActive(groupId, operatorId);

        if (memberRepository.swapOwner(groupId, operatorId, memberId) == 2)
            return;

        // đổi thất bại: tìm lý do để báo đúng mã lỗi, transaction sẽ rollback khi ném exception
        if (!memberRepository.existsByGroupIdAndUserIdAndStatus(groupId, memberId, MemberStatus.ACTIVE))
            throw new BusinessException(ErrorCode.GROUP_MEMBER_NOT_FOUND);

        throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    }

    //-----------------------------PRIVATE------------------------------------------//
    private MemberRes findOwner(UUID groupId) {
        return memberRepository.findByGroupIdAndRoleAndStatus(groupId, MemberRole.OWNER, MemberStatus.ACTIVE)
                .map(memberMapper::toResponse)
                .orElseThrow(() -> {
                    log.error("Data corruption: Group {} exists but has no ACTIVE OWNER!", groupId);
                    return new BusinessException(ErrorCode.INTERNAL_ERROR);
                });
    }


}

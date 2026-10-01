package com.datn.financeapp.group.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.request.member.MemberAddReq;
import com.datn.financeapp.group.dto.response.member.MemberRes;
import com.datn.financeapp.group.entity.Member;
import com.datn.financeapp.group.enums.MemberRole;
import com.datn.financeapp.group.enums.MemberStatus;
import com.datn.financeapp.group.events.MemberLeaveEvent;
import com.datn.financeapp.group.helper.MemberAuthInfo;
import com.datn.financeapp.group.helper.MemberViewEnricher;
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
import java.util.List;
import java.util.Objects;
import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MemberBehavierServiceImpl implements MemberBehavierService {

    // trạng thái hiển thị mặc định khi không lọc, bỏ qua LEFT và REMOVED
    private static final List<MemberStatus> VISIBLE_STATUSES = List.of(MemberStatus.ACTIVE, MemberStatus.PENDING);

    private final MemberRepository memberRepository;
    private final MemberMapper memberMapper;
    private final GroupPermissionValidator permissionValidator;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final MemberViewEnricher memberViewEnricher;

    @Override
    @Transactional(readOnly = true)
    public List<MemberRes> listMembers(UUID operatorId, UUID groupId, MemberStatus status) {
        MemberAuthInfo info = permissionValidator.getAuthInfo(groupId, operatorId);

        // người chờ duyệt chỉ chủ nhóm được xem
        if (status == MemberStatus.PENDING && !info.isOwner())
            throw new BusinessException(ErrorCode.FORBIDDEN_OWNER_REQUIRED);

        List<MemberStatus> statuses = resolveStatuses(status, info.isOwner());
        List<MemberRes> members = toResponses(memberRepository.findAllByGroupIdAndStatusIn(groupId, statuses));

        return memberViewEnricher.enrich(members, info.keepperId());
    }

    @Override
    @Transactional
    public List<MemberRes> ownerAddMembers(UUID operatorId, UUID groupId, MemberAddReq req) {

        List<UUID> memberIds = req.memberIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (memberIds.isEmpty())
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);

        // operatorId null là tự vào nhóm, còn lại phải là chủ nhóm
        if (operatorId != null)
            permissionValidator.verifyOwner(groupId, operatorId);

        //không cho thêm đã active hoặc pending
        assertNoneInGroup(groupId, memberIds);

        // danh sách member được thêm luôn là member và activate
        List<Member> members = memberIds.stream()
                .map(uid -> buildMember(groupId, uid))
                .toList();

        log.info("thêm {} thành viên vào nhóm {}", members.size(), groupId);

        return toResponses(memberRepository.saveAll(members));
    }


    @Override
    @Transactional
    public MemberRes ownerAddMember(UUID memberId, UUID groupId) {
        return ownerAddMembers(null, groupId, new MemberAddReq(List.of(memberId))).get(0);
    }

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
        permissionValidator.verifyOwner(groupId, operatorId);

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
        permissionValidator.verifyOwner(groupId, operatorId);

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
        permissionValidator.verifyOwner(groupId, operatorId);

        // không có dòng PENDING nào khớp thì báo không tìm thấy
        if (memberRepository.deletePending(groupId, memberId) == 0)
            throw new BusinessException(ErrorCode.GROUP_MEMBER_NOT_FOUND);
    }

    @Override
    public int rejectAll(UUID operatorId, UUID groupId) {
        // người gọi phải là chủ nhóm, nhóm phải đang ACTIVE
        permissionValidator.verifyOwner(groupId, operatorId);

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

        permissionValidator.verifyOwner(groupId, operatorId);

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
            throw new BusinessException(ErrorCode.CANNOT_TRANSFER_TO_SELF);

        // người gọi phải là chủ nhóm, nhóm phải đang ACTIVE
        permissionValidator.verifyOwner(groupId, operatorId);

        if (memberRepository.swapOwner(groupId, operatorId, memberId) == 2)
            return;

        // đổi thất bại: tìm lý do để báo đúng mã lỗi, transaction sẽ rollback khi ném exception
        if (!memberRepository.existsByGroupIdAndUserIdAndStatus(groupId, memberId, MemberStatus.ACTIVE))
            throw new BusinessException(ErrorCode.GROUP_MEMBER_NOT_FOUND);

        throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    }

    //-----------------------------PRIVATE------------------------------------------//
    // không lọc thì chủ nhóm thấy cả người chờ duyệt, thành viên thường chỉ thấy người đã vào nhóm
    private List<MemberStatus> resolveStatuses(MemberStatus status, boolean isOwner) {
        if (status != null)
            return List.of(status);
        return isOwner ? VISIBLE_STATUSES : List.of(MemberStatus.ACTIVE);
    }

    private MemberRes findOwner(UUID groupId) {
        return memberRepository.findByGroupIdAndRoleAndStatus(groupId, MemberRole.OWNER, MemberStatus.ACTIVE)
                .map(memberMapper::toResponse)
                .orElseThrow(() -> {
                    log.error("Data corruption: Group {} exists but has no ACTIVE OWNER!", groupId);
                    return new BusinessException(ErrorCode.INTERNAL_ERROR);
                });
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

    private Member buildMember(UUID groupId, UUID userId) {
        return Member.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .userId(userId)
                .role(MemberRole.MEMBER)
                .status(MemberStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build();
    }

    private List<MemberRes> toResponses(List<Member> members) {
        return members.stream()
                .map(memberMapper::toResponse)
                .toList();
    }

}

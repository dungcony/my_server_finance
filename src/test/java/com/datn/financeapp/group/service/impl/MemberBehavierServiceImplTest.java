package com.datn.financeapp.group.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.response.member.MemberRes;
import com.datn.financeapp.group.entity.Member;
import com.datn.financeapp.group.enums.GroupStatus;
import com.datn.financeapp.group.enums.MemberRole;
import com.datn.financeapp.group.enums.MemberStatus;
import com.datn.financeapp.group.helper.MemberAuthInfo;
import com.datn.financeapp.group.helper.MemberViewEnricher;
import com.datn.financeapp.group.mapper.MemberMapper;
import com.datn.financeapp.group.repository.MemberRepository;
import com.datn.financeapp.group.validator.GroupPermissionValidator;
import com.datn.financeapp.user.dto.response.UserRes;
import com.datn.financeapp.user.enums.UserStatus;
import com.datn.financeapp.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm thử {@link MemberBehavierServiceImpl#listMembers}: danh sách thành viên, quyền xem người chờ duyệt
 * và cờ thủ quỹ.
 */
@ExtendWith(MockitoExtension.class)
class MemberBehavierServiceImplTest {

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private MemberMapper memberMapper;
    @Mock
    private GroupPermissionValidator permissionValidator;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private UserService userService;

    private MemberBehavierServiceImpl service;

    private UUID groupId;
    private UUID ownerId;
    private UUID treasurerId;
    private UUID pendingUserId;

    @BeforeEach
    void setUp() {
        service = new MemberBehavierServiceImpl(memberRepository, memberMapper, permissionValidator,
                eventPublisher, new MemberViewEnricher(userService));
        groupId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        treasurerId = UUID.randomUUID();
        pendingUserId = UUID.randomUUID();

        // xem danh sách là thao tác đọc nên dùng bản xác thực cho phép nhóm lưu trữ
        lenient().when(permissionValidator.getAuthInfo(groupId, ownerId, true))
                .thenReturn(authInfo(ownerId, MemberRole.OWNER));
        lenient().when(permissionValidator.getAuthInfo(groupId, treasurerId, true))
                .thenReturn(authInfo(treasurerId, MemberRole.MEMBER));
        lenient().when(memberMapper.toResponse(any(Member.class))).thenAnswer(inv -> {
            Member m = inv.getArgument(0);
            return new MemberRes(m.getId(), m.getUserId(), m.getRole(), m.getStatus(), m.getJoinedAt());
        });
        lenient().when(userService.getNames(any(), isNull())).thenReturn(Map.of(
                ownerId, userNamed(ownerId, "Chủ nhóm"),
                treasurerId, userNamed(treasurerId, "Thủ quỹ"),
                pendingUserId, userNamed(pendingUserId, "Người xin vào")));
    }

    @Test
    @DisplayName("Chủ nhóm lọc PENDING thì thấy người chờ duyệt kèm tên hiển thị")
    void listMembers_OwnerFiltersPending_ReturnsPendingWithName() {
        when(memberRepository.findAllByGroupIdAndStatusIn(groupId, List.of(MemberStatus.PENDING)))
                .thenReturn(List.of(member(pendingUserId, MemberRole.MEMBER, MemberStatus.PENDING)));

        List<MemberRes> result = service.listMembers(ownerId, groupId, MemberStatus.PENDING);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo(pendingUserId);
        assertThat(result.get(0).displayName()).isEqualTo("Người xin vào");
        assertThat(result.get(0).isTreasurer()).isFalse();
    }

    @Test
    @DisplayName("Thành viên thường lọc PENDING thì bị chặn FORBIDDEN_OWNER_REQUIRED và không đụng vào CSDL")
    void listMembers_NormalMemberFiltersPending_Forbidden() {
        assertThatThrownBy(() -> service.listMembers(treasurerId, groupId, MemberStatus.PENDING))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.FORBIDDEN_OWNER_REQUIRED.getCode());

        verify(memberRepository, never()).findAllByGroupIdAndStatusIn(any(), any());
    }

    @Test
    @DisplayName("Không lọc: chủ nhóm thấy cả ACTIVE và PENDING")
    void listMembers_OwnerNoFilter_SeesActiveAndPending() {
        when(memberRepository.findAllByGroupIdAndStatusIn(eq(groupId), any())).thenReturn(List.of());

        service.listMembers(ownerId, groupId, null);

        verify(memberRepository)
                .findAllByGroupIdAndStatusIn(groupId, List.of(MemberStatus.ACTIVE, MemberStatus.PENDING));
    }

    @Test
    @DisplayName("Không lọc: thành viên thường chỉ thấy ACTIVE")
    void listMembers_NormalMemberNoFilter_SeesOnlyActive() {
        when(memberRepository.findAllByGroupIdAndStatusIn(eq(groupId), any())).thenReturn(List.of());

        service.listMembers(treasurerId, groupId, null);

        verify(memberRepository).findAllByGroupIdAndStatusIn(groupId, List.of(MemberStatus.ACTIVE));
    }

    @Test
    @DisplayName("Chỉ đúng người đang giữ quỹ được đánh dấu thủ quỹ")
    void listMembers_MarksOnlyKeeperAsTreasurer() {
        when(memberRepository.findAllByGroupIdAndStatusIn(eq(groupId), any())).thenReturn(List.of(
                member(ownerId, MemberRole.OWNER, MemberStatus.ACTIVE),
                member(treasurerId, MemberRole.MEMBER, MemberStatus.ACTIVE)));

        List<MemberRes> result = service.listMembers(ownerId, groupId, MemberStatus.ACTIVE);

        assertThat(result).extracting(MemberRes::userId, MemberRes::isTreasurer)
                .containsExactly(tuple(ownerId, false), tuple(treasurerId, true));
    }

    @Test
    @DisplayName("Xem danh sách thành viên dùng bản xác thực cho phép nhóm lưu trữ (thao tác đọc)")
    void listMembers_UsesAllowArchivedValidation() {
        when(memberRepository.findAllByGroupIdAndStatusIn(eq(groupId), any())).thenReturn(List.of());

        service.listMembers(ownerId, groupId, null);

        verify(permissionValidator).getAuthInfo(groupId, ownerId, true);
        verify(permissionValidator, never()).getAuthInfo(groupId, ownerId);
    }

    // bản ghi người dùng chỉ cần tên: lastName null thì tên hiển thị chính là firstName
    private UserRes userNamed(UUID id, String firstName) {
        return new UserRes(id, null, firstName, null, null, UserStatus.ACTIVE, null, null, null, false);
    }

    // người giữ quỹ của nhóm trong test luôn là treasurerId
    private MemberAuthInfo authInfo(UUID userId, MemberRole role) {
        return new MemberAuthInfo(groupId, userId, GroupStatus.ACTIVE, true, MemberStatus.ACTIVE, role, treasurerId);
    }

    private Member member(UUID userId, MemberRole role, MemberStatus status) {
        return Member.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .userId(userId)
                .role(role)
                .status(status)
                .joinedAt(status == MemberStatus.ACTIVE ? Instant.now() : null)
                .build();
    }
}

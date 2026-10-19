package com.datn.financeapp.unitTest.group.service.impl;

import com.datn.financeapp.module.group.service.impl.*;


import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.group.dto.response.member.MemberRes;
import com.datn.financeapp.module.group.entity.Member;
import com.datn.financeapp.module.group.enums.GroupStatus;
import com.datn.financeapp.module.group.enums.MemberRole;
import com.datn.financeapp.module.group.enums.MemberStatus;
import com.datn.financeapp.module.group.helper.MemberAuthCache;
import com.datn.financeapp.module.group.helper.MemberAuthInfo;
import com.datn.financeapp.module.group.helper.MemberViewEnricher;
import com.datn.financeapp.module.group.mapper.MemberMapper;
import com.datn.financeapp.module.group.repository.MemberRepository;
import com.datn.financeapp.module.user.dto.response.UserNameDisplayRes;
import com.datn.financeapp.module.user.enums.UserStatus;
import com.datn.financeapp.module.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private MemberAuthCache authCache;
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
        service = new MemberBehavierServiceImpl(memberRepository, memberMapper, authCache,
                eventPublisher, new MemberViewEnricher(userService));
        groupId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        treasurerId = UUID.randomUUID();
        pendingUserId = UUID.randomUUID();

        lenient().when(authCache.get(groupId, ownerId))
                .thenReturn(Optional.of(authInfo(ownerId, MemberRole.OWNER)));
        lenient().when(authCache.get(groupId, treasurerId))
                .thenReturn(Optional.of(authInfo(treasurerId, MemberRole.MEMBER)));
        lenient().when(memberMapper.toResponse(any(Member.class))).thenAnswer(inv -> {
            Member m = inv.getArgument(0);
            return new MemberRes(m.getId(), m.getUserId(), m.getRole(), m.getStatus(), m.getJoinedAt());
        });
        lenient().when(userService.getNames(any())).thenReturn(Map.of(
                ownerId, userNamed(ownerId, "Chủ nhóm"),
                treasurerId, userNamed(treasurerId, "Thủ quỹ"),
                pendingUserId, userNamed(pendingUserId, "Người xin vào")));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId.toString(), null, Collections.emptyList())
        );
    }

    @Test
    @DisplayName("Chủ nhóm lọc PENDING thì thấy người chờ duyệt kèm tên hiển thị")
    void listMembers_OwnerFiltersPending_ReturnsPendingWithName() {
        authAs(ownerId);
        when(memberRepository.findAllByGroupIdAndStatusIn(groupId, List.of(MemberStatus.PENDING)))
                .thenReturn(List.of(member(pendingUserId, MemberRole.MEMBER, MemberStatus.PENDING)));

        List<MemberRes> result = service.listMembers(groupId, MemberStatus.PENDING);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo(pendingUserId);
        assertThat(result.get(0).displayName()).isEqualTo("Người xin vào");
        assertThat(result.get(0).isTreasurer()).isFalse();
    }

    @Test
    @DisplayName("Thành viên thường lọc PENDING thì bị chặn GROUP_OWNER_REQUIRED và không đụng vào CSDL")
    void listMembers_NormalMemberFiltersPending_Forbidden() {
        authAs(treasurerId);
        assertThatThrownBy(() -> service.listMembers(groupId, MemberStatus.PENDING))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GROUP_OWNER_REQUIRED.getCode());

        verify(memberRepository, never()).findAllByGroupIdAndStatusIn(any(), any());
    }

    @Test
    @DisplayName("Không lọc: chủ nhóm thấy cả ACTIVE và PENDING")
    void listMembers_OwnerNoFilter_SeesActiveAndPending() {
        authAs(ownerId);
        when(memberRepository.findAllByGroupIdAndStatusIn(eq(groupId), any())).thenReturn(List.of());

        service.listMembers(groupId, null);

        verify(memberRepository)
                .findAllByGroupIdAndStatusIn(groupId, List.of(MemberStatus.ACTIVE, MemberStatus.PENDING));
    }

    @Test
    @DisplayName("Không lọc: thành viên thường chỉ thấy ACTIVE")
    void listMembers_NormalMemberNoFilter_SeesOnlyActive() {
        authAs(treasurerId);
        when(memberRepository.findAllByGroupIdAndStatusIn(eq(groupId), any())).thenReturn(List.of());

        service.listMembers(groupId, null);

        verify(memberRepository).findAllByGroupIdAndStatusIn(groupId, List.of(MemberStatus.ACTIVE));
    }

    @Test
    @DisplayName("Chỉ đúng người đang giữ quỹ được đánh dấu thủ quỹ")
    void listMembers_MarksOnlyKeeperAsTreasurer() {
        authAs(ownerId);
        when(memberRepository.findAllByGroupIdAndStatusIn(eq(groupId), any())).thenReturn(List.of(
                member(ownerId, MemberRole.OWNER, MemberStatus.ACTIVE),
                member(treasurerId, MemberRole.MEMBER, MemberStatus.ACTIVE)));

        List<MemberRes> result = service.listMembers(groupId, MemberStatus.ACTIVE);

        assertThat(result).extracting(MemberRes::userId, MemberRes::isTreasurer)
                .containsExactly(tuple(ownerId, false), tuple(treasurerId, true));
    }

    private UserNameDisplayRes userNamed(UUID id, String firstName) {
        return new UserNameDisplayRes(id, firstName, null, UserStatus.ACTIVE, false);
    }

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
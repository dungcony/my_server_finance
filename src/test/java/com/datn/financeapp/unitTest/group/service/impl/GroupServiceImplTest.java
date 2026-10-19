package com.datn.financeapp.unitTest.group.service.impl;

import com.datn.financeapp.module.group.service.impl.*;


import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.group.dto.request.member.MemberCreateReq;
import com.datn.financeapp.module.group.dto.response.member.MemberRes;
import com.datn.financeapp.module.group.entity.Fund;
import com.datn.financeapp.module.group.entity.Group;
import com.datn.financeapp.module.group.enums.GroupStatus;
import com.datn.financeapp.module.group.enums.MemberRole;
import com.datn.financeapp.module.group.enums.MemberStatus;
import com.datn.financeapp.module.group.helper.MemberAuthCache;
import com.datn.financeapp.module.group.helper.MemberAuthInfo;
import com.datn.financeapp.module.group.helper.MemberBalances;
import com.datn.financeapp.module.group.helper.MemberViewEnricher;
import com.datn.financeapp.module.group.mapper.FundMapper;
import com.datn.financeapp.module.group.mapper.GroupMapper;
import com.datn.financeapp.module.group.repository.GroupRepository;
import com.datn.financeapp.module.group.service.FundService;
import com.datn.financeapp.module.group.service.GroupTxnGetService;
import com.datn.financeapp.module.group.service.MemberBalanceService;
import com.datn.financeapp.module.group.service.MemberService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceImplTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupTxnGetService groupTxnGetService;

    @Mock
    private MemberService memberService;

    @Mock
    private FundService fundService;

    @Mock
    private MemberAuthCache authCache;

    @Mock
    private MemberBalanceService memberBalanceService;

    @Mock
    private GroupMapper groupMapper;

    @Mock
    private FundMapper fundMapper;

    @Mock
    private MemberViewEnricher memberViewEnricher;

    @InjectMocks
    private GroupServiceImpl groupService;

    private final UUID groupId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private static final String INVITE_CODE = "ABCD1234";

    private Group buildGroup(GroupStatus status, boolean isJoinWithoutConfirm) {
        return Group.builder()
                .id(groupId)
                .name("Nhóm kiểm thử")
                .inviteCode(INVITE_CODE)
                .status(status)
                .isSettlementEnabled(true)
                .isJoinWithoutConfirm(isJoinWithoutConfirm)
                .build();
    }

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId.toString(), null, Collections.emptyList())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("joinByCode tests")
    class JoinByCodeTests {

        private MemberCreateReq captureCreatedMember() {
            ArgumentCaptor<MemberCreateReq> captor = ArgumentCaptor.forClass(MemberCreateReq.class);
            verify(memberService).create(captor.capture());
            return captor.getValue();
        }

        @Test
        @DisplayName("Nhóm không cần duyệt thì người tham gia vào thẳng ACTIVE với vai trò MEMBER")
        void joinByCode_joinWithoutConfirm_createsActiveMember() {
            when(groupRepository.findByInviteCodeAndStatusNot(INVITE_CODE, GroupStatus.DELETED))
                    .thenReturn(Optional.of(buildGroup(GroupStatus.ACTIVE, true)));

            groupService.joinByCode(INVITE_CODE);

            MemberCreateReq created = captureCreatedMember();
            assertThat(created.groupId()).isEqualTo(groupId);
            assertThat(created.userId()).isEqualTo(userId);
            assertThat(created.role()).isEqualTo(MemberRole.MEMBER);
            assertThat(created.status()).isEqualTo(MemberStatus.ACTIVE);
        }

        @Test
        @DisplayName("Nhóm cần duyệt thì người tham gia ở trạng thái PENDING chờ chủ nhóm")
        void joinByCode_needsConfirm_createsPendingMember() {
            when(groupRepository.findByInviteCodeAndStatusNot(INVITE_CODE, GroupStatus.DELETED))
                    .thenReturn(Optional.of(buildGroup(GroupStatus.ACTIVE, false)));

            groupService.joinByCode(INVITE_CODE);

            assertThat(captureCreatedMember().status()).isEqualTo(MemberStatus.PENDING);
        }

        @Test
        @DisplayName("Mã mời không khớp nhóm nào thì ném GROUP_NOT_FOUND và không tạo thành viên")
        void joinByCode_unknownInviteCode_throwsGroupNotFound() {
            when(groupRepository.findByInviteCodeAndStatusNot(INVITE_CODE, GroupStatus.DELETED))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> groupService.joinByCode(INVITE_CODE))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.GROUP_NOT_FOUND.getCode());

            verify(memberService, never()).create(any());
        }

        @Test
        @DisplayName("Nhóm đã lưu trữ thì ném GROUP_ARCHIVED và không tạo thành viên")
        void joinByCode_archivedGroup_throwsGroupArchived() {
            when(groupRepository.findByInviteCodeAndStatusNot(INVITE_CODE, GroupStatus.DELETED))
                    .thenReturn(Optional.of(buildGroup(GroupStatus.ARCHIVED, true)));

            assertThatThrownBy(() -> groupService.joinByCode(INVITE_CODE))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.GROUP_ARCHIVED.getCode());

            verify(memberService, never()).create(any());
        }

        @Test
        @DisplayName("Người đã ACTIVE trong nhóm thì lỗi GROUP_MEMBER_ALREADY_EXISTS lan ra và không tạo thêm dòng thành viên")
        void joinByCode_alreadyActiveMember_throwsAndDoesNotCreate() {
            when(groupRepository.findByInviteCodeAndStatusNot(INVITE_CODE, GroupStatus.DELETED))
                    .thenReturn(Optional.of(buildGroup(GroupStatus.ACTIVE, true)));
            doThrow(new BusinessException(ErrorCode.GROUP_MEMBER_ALREADY_EXISTS))
                    .when(memberService).assertNotInGroup(groupId, userId);

            assertThatThrownBy(() -> groupService.joinByCode(INVITE_CODE))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.GROUP_MEMBER_ALREADY_EXISTS.getCode());

            verify(memberService, never()).create(any());
        }

        @Test
        @DisplayName("Người đang PENDING xin vào thì lỗi GROUP_MEMBER_PENDING lan ra và không tạo thêm dòng thành viên")
        void joinByCode_alreadyPendingMember_throwsAndDoesNotCreate() {
            when(groupRepository.findByInviteCodeAndStatusNot(INVITE_CODE, GroupStatus.DELETED))
                    .thenReturn(Optional.of(buildGroup(GroupStatus.ACTIVE, false)));
            doThrow(new BusinessException(ErrorCode.GROUP_MEMBER_PENDING))
                    .when(memberService).assertNotInGroup(groupId, userId);

            assertThatThrownBy(() -> groupService.joinByCode(INVITE_CODE))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.GROUP_MEMBER_PENDING.getCode());

            verify(memberService, never()).create(any());
        }
    }

    @Nested
    @DisplayName("Thao tác trên nhóm đã lưu trữ")
    class ArchivedGroupTests {

        @Test
        @DisplayName("get: thành viên xem được chi tiết nhóm đã lưu trữ, tư cách thành viên do danh sách ACTIVE quyết định")
        void detail_archivedGroup_stillReadable() {
            Group group = buildGroup(GroupStatus.ARCHIVED, true);
            when(groupRepository.findNotDeletedWithFundById(groupId)).thenReturn(Optional.of(group));
            when(memberService.getActivateMembers(groupId)).thenReturn(List.of(
                    new MemberRes(UUID.randomUUID(), userId, MemberRole.MEMBER, MemberStatus.ACTIVE, Instant.now())));

            groupService.detail(groupId);

            verify(groupMapper).toDetailResponse(eq(group), eq(MemberRole.MEMBER), any(), any());
        }

        @Test
        @DisplayName("get: người ngoài nhóm có thật nhận GROUP_MEMBER_REQUIRED (rule.md quy tắc 8)")
        void detail_nonMember_throwsForbiddenNotGroupMember() {
            Group group = buildGroup(GroupStatus.ACTIVE, true);
            when(groupRepository.findNotDeletedWithFundById(groupId)).thenReturn(Optional.of(group));
            when(memberService.getActivateMembers(groupId)).thenReturn(List.of(
                    new MemberRes(UUID.randomUUID(), UUID.randomUUID(), MemberRole.OWNER, MemberStatus.ACTIVE,
                            Instant.now())));

            assertThatThrownBy(() -> groupService.detail(groupId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.GROUP_MEMBER_REQUIRED.getCode());

            verify(groupMapper, never()).toDetailResponse(any(), any(), any(), any());
        }

        @Test
        @DisplayName("get: nhóm không tồn tại hoặc đã xoá thì GROUP_NOT_FOUND")
        void detail_missingGroup_throwsGroupNotFound() {
            when(groupRepository.findNotDeletedWithFundById(groupId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> groupService.detail(groupId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.GROUP_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("delete: chủ nhóm xoá được nhóm đã lưu trữ")
        void delete_archivedGroup_allowed() {
            Group group = buildGroup(GroupStatus.ARCHIVED, true);
            group.setFund(Fund.builder().currentBalance(0L).build());
            when(authCache.get(groupId, userId)).thenReturn(Optional.of(
                    new MemberAuthInfo(groupId, userId, GroupStatus.ARCHIVED, true, MemberStatus.ACTIVE, MemberRole.OWNER, userId)
            ));
            when(groupRepository.findNotDeletedWithFundById(groupId)).thenReturn(Optional.of(group));
            when(groupTxnGetService.countUnresolvedForGroup(groupId)).thenReturn(0L);
            when(memberBalanceService.getBalances(groupId)).thenReturn(MemberBalances.from(List.of()));

            groupService.delete(groupId);

            assertThat(group.getStatus()).isEqualTo(GroupStatus.DELETED);
            verify(groupRepository).save(group);
        }

        @Test
        @DisplayName("unarchive: chủ nhóm mở lại nhóm đã lưu trữ")
        void unarchive_archivedGroup_reactivates() {
            Group group = buildGroup(GroupStatus.ARCHIVED, true);
            when(authCache.get(groupId, userId)).thenReturn(Optional.of(
                    new MemberAuthInfo(groupId, userId, GroupStatus.ARCHIVED, true, MemberStatus.ACTIVE, MemberRole.OWNER, userId)
            ));
            when(groupRepository.findArchivedWithFundById(groupId)).thenReturn(Optional.of(group));

            groupService.unarchive(groupId);

            assertThat(group.getStatus()).isEqualTo(GroupStatus.ACTIVE);
            verify(groupRepository).save(group);
        }

        @Test
        @DisplayName("archive: chuyển trạng thái nhóm sang ARCHIVED")
        void archive_setsStatusArchived() {
            Group group = buildGroup(GroupStatus.ACTIVE, true);
            when(authCache.get(groupId, userId)).thenReturn(Optional.of(
                    new MemberAuthInfo(groupId, userId, GroupStatus.ACTIVE, true, MemberStatus.ACTIVE, MemberRole.OWNER, userId)
            ));
            when(groupRepository.findActivatedWithFundById(groupId)).thenReturn(Optional.of(group));
            when(groupTxnGetService.countUnresolvedForGroup(groupId)).thenReturn(0L);

            groupService.archive(groupId);

            assertThat(group.getStatus()).isEqualTo(GroupStatus.ARCHIVED);
            verify(groupRepository).save(group);
        }
    }
}

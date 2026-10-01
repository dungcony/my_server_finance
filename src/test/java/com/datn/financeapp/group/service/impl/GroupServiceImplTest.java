package com.datn.financeapp.group.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.request.group.GroupJoinReq;
import com.datn.financeapp.group.dto.request.member.MemberCreateReq;
import com.datn.financeapp.group.dto.response.group.GroupPendingCountRes;
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
import com.datn.financeapp.group.service.MemberService;
import com.datn.financeapp.group.validator.GroupPermissionValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceImplTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GTransactionService gTransactionService;

    @Mock
    private MemberService memberService;

    @Mock
    private FundService fundService;

    @Mock
    private GroupPermissionValidator permissionValidator;

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
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
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

            groupService.joinByCode(userId, new GroupJoinReq(INVITE_CODE));

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

            groupService.joinByCode(userId, new GroupJoinReq(INVITE_CODE));

            assertThat(captureCreatedMember().status()).isEqualTo(MemberStatus.PENDING);
        }

        @Test
        @DisplayName("Mã mời không khớp nhóm nào thì ném GROUP_NOT_FOUND và không tạo thành viên")
        void joinByCode_unknownInviteCode_throwsGroupNotFound() {
            when(groupRepository.findByInviteCodeAndStatusNot(INVITE_CODE, GroupStatus.DELETED))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> groupService.joinByCode(userId, new GroupJoinReq(INVITE_CODE)))
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

            assertThatThrownBy(() -> groupService.joinByCode(userId, new GroupJoinReq(INVITE_CODE)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.GROUP_ARCHIVED.getCode());

            verify(memberService, never()).create(any());
        }

        @Test
        @DisplayName("Người đã ACTIVE trong nhóm thì lỗi ALREADY_IN_GROUP lan ra và không tạo thêm dòng thành viên")
        void joinByCode_alreadyActiveMember_throwsAndDoesNotCreate() {
            when(groupRepository.findByInviteCodeAndStatusNot(INVITE_CODE, GroupStatus.DELETED))
                    .thenReturn(Optional.of(buildGroup(GroupStatus.ACTIVE, true)));
            doThrow(new BusinessException(ErrorCode.ALREADY_IN_GROUP))
                    .when(memberService).assertNotInGroup(groupId, userId);

            assertThatThrownBy(() -> groupService.joinByCode(userId, new GroupJoinReq(INVITE_CODE)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.ALREADY_IN_GROUP.getCode());

            verify(memberService, never()).create(any());
        }

        @Test
        @DisplayName("Người đang PENDING xin vào thì lỗi PENDING_IN_GROUP lan ra và không tạo thêm dòng thành viên")
        void joinByCode_alreadyPendingMember_throwsAndDoesNotCreate() {
            when(groupRepository.findByInviteCodeAndStatusNot(INVITE_CODE, GroupStatus.DELETED))
                    .thenReturn(Optional.of(buildGroup(GroupStatus.ACTIVE, false)));
            doThrow(new BusinessException(ErrorCode.PENDING_IN_GROUP))
                    .when(memberService).assertNotInGroup(groupId, userId);

            assertThatThrownBy(() -> groupService.joinByCode(userId, new GroupJoinReq(INVITE_CODE)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.PENDING_IN_GROUP.getCode());

            verify(memberService, never()).create(any());
        }
    }

    @Nested
    @DisplayName("pendingCount")
    class PendingCountTests {

        private MemberAuthInfo info(MemberRole role, UUID keeperId) {
            return new MemberAuthInfo(groupId, userId, GroupStatus.ACTIVE, true, MemberStatus.ACTIVE, role, keeperId);
        }

        @Test
        @DisplayName("Chủ nhóm thấy cả số giao dịch lẫn số thành viên đang chờ duyệt")
        void pendingCount_owner_seesBoth() {
            when(permissionValidator.getAuthInfo(groupId, userId)).thenReturn(info(MemberRole.OWNER, UUID.randomUUID()));
            when(gTransactionService.countPendingForGroup(groupId)).thenReturn(3L);
            when(memberService.countPendingMembers(groupId)).thenReturn(2L);

            GroupPendingCountRes res = groupService.pendingCount(userId, groupId);

            assertThat(res.pendingTransactions()).isEqualTo(3L);
            assertThat(res.pendingMembers()).isEqualTo(2L);
        }

        @Test
        @DisplayName("Thủ quỹ không phải chủ nhóm chỉ thấy số giao dịch, số thành viên chờ là 0")
        void pendingCount_treasurer_seesOnlyTransactions() {
            when(permissionValidator.getAuthInfo(groupId, userId)).thenReturn(info(MemberRole.MEMBER, userId));
            when(gTransactionService.countPendingForGroup(groupId)).thenReturn(3L);

            GroupPendingCountRes res = groupService.pendingCount(userId, groupId);

            assertThat(res.pendingTransactions()).isEqualTo(3L);
            assertThat(res.pendingMembers()).isZero();
            verify(memberService, never()).countPendingMembers(any());
        }

        @Test
        @DisplayName("Thành viên thường không có quyền duyệt nên cả hai số đều là 0 và không truy vấn đếm")
        void pendingCount_normalMember_seesZero() {
            when(permissionValidator.getAuthInfo(groupId, userId))
                    .thenReturn(info(MemberRole.MEMBER, UUID.randomUUID()));

            GroupPendingCountRes res = groupService.pendingCount(userId, groupId);

            assertThat(res.pendingTransactions()).isZero();
            assertThat(res.pendingMembers()).isZero();
            verify(gTransactionService, never()).countPendingForGroup(any());
            verify(memberService, never()).countPendingMembers(any());
        }
    }
}

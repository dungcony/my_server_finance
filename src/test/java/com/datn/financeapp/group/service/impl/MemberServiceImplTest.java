package com.datn.financeapp.group.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.request.member.MemberAddReq;
import com.datn.financeapp.group.dto.request.member.MemberCreateReq;
import com.datn.financeapp.group.dto.response.member.MemberRes;
import com.datn.financeapp.group.entity.Member;
import com.datn.financeapp.group.enums.MemberRole;
import com.datn.financeapp.group.enums.MemberStatus;
import com.datn.financeapp.group.mapper.MemberMapper;
import com.datn.financeapp.group.repository.MemberRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberServiceImplTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberMapper memberMapper;

    @Mock
    private com.datn.financeapp.group.validator.GroupPermissionValidator permissionValidator;

    @Mock
    private org.springframework.context.ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private MemberServiceImpl groupMemberService;

    @Nested
    @DisplayName("create tests")
    class CreateTests {

        private final UUID groupId = UUID.randomUUID();
        private final UUID userId = UUID.randomUUID();

        private void stubMapperAndSave(MemberCreateReq req) {
            when(memberRepository.findByGroupIdAndUserIdInAndStatusIn(eq(groupId), any(), any()))
                    .thenReturn(List.of());
            when(memberMapper.toEntity(req)).thenReturn(Member.builder()
                    .groupId(groupId)
                    .userId(userId)
                    .role(req.role())
                    .status(req.status())
                    .build());
            when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        @DisplayName("Không truyền role và status thì mặc định MEMBER, PENDING và chưa có joinedAt")
        void create_defaultsToMemberPending() {
            MemberCreateReq req = new MemberCreateReq(groupId, userId, null, null);
            stubMapperAndSave(req);

            Member result = groupMemberService.create(req).orElseThrow();

            assertThat(result.getId()).isNotNull();
            assertThat(result.getRole()).isEqualTo(MemberRole.MEMBER);
            assertThat(result.getStatus()).isEqualTo(MemberStatus.PENDING);
            assertThat(result.getJoinedAt()).isNull();
        }

        @Test
        @DisplayName("Trạng thái ACTIVE thì ghi joinedAt và giữ nguyên role được truyền vào")
        void create_activeSetsJoinedAt() {
            MemberCreateReq req = new MemberCreateReq(groupId, userId, MemberRole.OWNER, MemberStatus.ACTIVE);
            stubMapperAndSave(req);

            Member result = groupMemberService.create(req).orElseThrow();

            assertThat(result.getRole()).isEqualTo(MemberRole.OWNER);
            assertThat(result.getStatus()).isEqualTo(MemberStatus.ACTIVE);
            assertThat(result.getJoinedAt()).isNotNull();
        }

        @Test
        @DisplayName("Ném PENDING_IN_GROUP khi người dùng đang chờ duyệt trong nhóm")
        void create_throwsWhenAlreadyPending() {
            MemberCreateReq req = new MemberCreateReq(groupId, userId, null, null);
            when(memberRepository.findByGroupIdAndUserIdInAndStatusIn(eq(groupId), any(), any()))
                    .thenReturn(List.of(Member.builder().groupId(groupId).userId(userId)
                            .status(MemberStatus.PENDING).build()));

            assertThatThrownBy(() -> groupMemberService.create(req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.PENDING_IN_GROUP.getCode());
            verify(memberRepository, never()).save(any());
        }

        @Test
        @DisplayName("Ném VALIDATION_ERROR khi tạo thẳng với trạng thái LEFT hoặc REMOVED")
        void create_rejectsLeftAndRemoved() {
            for (MemberStatus status : List.of(MemberStatus.LEFT, MemberStatus.REMOVED)) {
                MemberCreateReq req = new MemberCreateReq(groupId, userId, null, status);

                assertThatThrownBy(() -> groupMemberService.create(req))
                        .isInstanceOf(BusinessException.class)
                        .extracting("code")
                        .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
            }
            verify(memberRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("addMember tests")
    class AddMemberTests {

        @Test
        @DisplayName("Thêm 1 thành viên thành công")
        void addMember_Success() {
            UUID groupId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Instant now = Instant.now();

            Member savedMember = Member.builder()
                    .id(UUID.randomUUID())
                    .groupId(groupId)
                    .userId(userId)
                    .role(MemberRole.MEMBER)
                    .status(MemberStatus.ACTIVE)
                    .joinedAt(now)
                    .build();

            MemberRes expectedRes = new MemberRes(
                    savedMember.getId(),
                    userId,
                    MemberRole.MEMBER,
                    MemberStatus.ACTIVE,
                    now
            );

            when(memberRepository.findByGroupIdAndUserIdInAndStatusIn(eq(groupId), any(), any()))
                    .thenReturn(List.of());
            when(memberRepository.saveAll(any())).thenReturn(List.of(savedMember));
            when(memberMapper.toResponse(savedMember)).thenReturn(expectedRes);

            MemberRes result = groupMemberService.addMember(userId, groupId, MemberStatus.ACTIVE, now);

            assertThat(result).isNotNull();
            assertThat(result.userId()).isEqualTo(userId);
            assertThat(result.role()).isEqualTo(MemberRole.MEMBER);
            verify(memberRepository).saveAll(argThat(iterable -> {
                List<Member> list = new ArrayList<>();
                iterable.forEach(list::add);
                return list.size() == 1
                        && list.get(0).getGroupId().equals(groupId)
                        && list.get(0).getUserId().equals(userId)
                        && list.get(0).getRole() == MemberRole.MEMBER
                        && list.get(0).getStatus() == MemberStatus.ACTIVE
                        && list.get(0).getJoinedAt().equals(now);
            }));
        }
    }

    @Nested
    @DisplayName("addMembers tests")
    class AddMembersTests {

        @Test
        @DisplayName("Ném VALIDATION_ERROR khi danh sách memberIds rỗng")
        void addMembers_EmptyList_ThrowsException() {
            UUID operatorId = UUID.randomUUID();
            UUID groupId = UUID.randomUUID();
            MemberAddReq req = new MemberAddReq(List.of(), MemberStatus.ACTIVE, MemberRole.MEMBER, Instant.now());

            assertThatThrownBy(() -> groupMemberService.addMembers(operatorId, groupId, req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());

            verify(memberRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("Ném VALIDATION_ERROR khi danh sách memberIds chỉ chứa phần tử null")
        void addMembers_OnlyNullElements_ThrowsException() {
            UUID operatorId = UUID.randomUUID();
            UUID groupId = UUID.randomUUID();
            List<UUID> input = new ArrayList<>();
            input.add(null);
            input.add(null);
            MemberAddReq req = new MemberAddReq(input, MemberStatus.ACTIVE, MemberRole.MEMBER, Instant.now());

            assertThatThrownBy(() -> groupMemberService.addMembers(operatorId, groupId, req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());

            verify(memberRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("Ném ALREADY_IN_GROUP khi thành viên đã có trạng thái ACTIVE trong nhóm")
        void addMembers_MemberAlreadyActive_ThrowsAlreadyInGroup() {
            UUID operatorId = UUID.randomUUID();
            UUID groupId = UUID.randomUUID();
            UUID user1 = UUID.randomUUID();
            MemberAddReq req = new MemberAddReq(List.of(user1), MemberStatus.ACTIVE, MemberRole.MEMBER, Instant.now());

            Member existingMember = Member.builder()
                    .id(UUID.randomUUID())
                    .groupId(groupId)
                    .userId(user1)
                    .status(MemberStatus.ACTIVE)
                    .build();

            when(memberRepository.findByGroupIdAndUserIdInAndStatusIn(eq(groupId), any(), any()))
                    .thenReturn(List.of(existingMember));

            assertThatThrownBy(() -> groupMemberService.addMembers(operatorId, groupId, req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.ALREADY_IN_GROUP.getCode());

            verify(memberRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("Ném PENDING_IN_GROUP khi thành viên đang ở trạng thái PENDING trong nhóm")
        void addMembers_MemberPending_ThrowsPendingInGroup() {
            UUID operatorId = UUID.randomUUID();
            UUID groupId = UUID.randomUUID();
            UUID user1 = UUID.randomUUID();
            MemberAddReq req = new MemberAddReq(List.of(user1), MemberStatus.ACTIVE, MemberRole.MEMBER, Instant.now());

            Member existingMember = Member.builder()
                    .id(UUID.randomUUID())
                    .groupId(groupId)
                    .userId(user1)
                    .status(MemberStatus.PENDING)
                    .build();

            when(memberRepository.findByGroupIdAndUserIdInAndStatusIn(eq(groupId), any(), any()))
                    .thenReturn(List.of(existingMember));

            assertThatThrownBy(() -> groupMemberService.addMembers(operatorId, groupId, req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.PENDING_IN_GROUP.getCode());

            verify(memberRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("Thêm danh sách thành viên thành công, tự động loại bỏ trùng lặp và null")
        void addMembers_Success_DeduplicatesAndFiltersNulls() {
            UUID groupId = UUID.randomUUID();
            UUID user1 = UUID.randomUUID();
            UUID user2 = UUID.randomUUID();
            Instant now = Instant.now();

            List<UUID> input = Arrays.asList(user1, user2, user1, null);
            MemberAddReq req = new MemberAddReq(input, MemberStatus.ACTIVE, MemberRole.MEMBER, now);

            Member member1 = Member.builder()
                    .id(UUID.randomUUID())
                    .groupId(groupId)
                    .userId(user1)
                    .role(MemberRole.MEMBER)
                    .status(MemberStatus.ACTIVE)
                    .joinedAt(now)
                    .build();

            Member member2 = Member.builder()
                    .id(UUID.randomUUID())
                    .groupId(groupId)
                    .userId(user2)
                    .role(MemberRole.MEMBER)
                    .status(MemberStatus.ACTIVE)
                    .joinedAt(now)
                    .build();

            List<Member> savedMembers = List.of(member1, member2);

            MemberRes res1 = new MemberRes(member1.getId(), user1, MemberRole.MEMBER, MemberStatus.ACTIVE, now);
            MemberRes res2 = new MemberRes(member2.getId(), user2, MemberRole.MEMBER, MemberStatus.ACTIVE, now);

            when(memberRepository.findByGroupIdAndUserIdInAndStatusIn(eq(groupId), any(), any()))
                    .thenReturn(List.of());
            when(memberRepository.saveAll(any())).thenReturn(savedMembers);
            when(memberMapper.toResponse(member1)).thenReturn(res1);
            when(memberMapper.toResponse(member2)).thenReturn(res2);

            UUID operatorId = UUID.randomUUID();
            List<MemberRes> result = groupMemberService.addMembers(operatorId, groupId, req);

            assertThat(result).hasSize(2);
            assertThat(result).containsExactly(res1, res2);

            verify(memberRepository).saveAll(argThat(iterable -> {
                List<Member> list = new ArrayList<>();
                iterable.forEach(list::add);
                return list.size() == 2
                        && list.stream().anyMatch(m -> m.getUserId().equals(user1) && m.getRole() == MemberRole.MEMBER)
                        && list.stream().anyMatch(m -> m.getUserId().equals(user2) && m.getRole() == MemberRole.MEMBER);
            }));
        }

        @Test
        @DisplayName("Thêm danh sách khi addAt là null thì tự gán Instant hiện tại")
        void addMembers_WithNullNow_UsesCurrentInstant() {
            UUID operatorId = UUID.randomUUID();
            UUID groupId = UUID.randomUUID();
            UUID user1 = UUID.randomUUID();
            MemberAddReq req = new MemberAddReq(List.of(user1), MemberStatus.ACTIVE, MemberRole.MEMBER, null);

            when(memberRepository.findByGroupIdAndUserIdInAndStatusIn(eq(groupId), any(), any()))
                    .thenReturn(List.of());
            when(memberRepository.saveAll(any())).thenAnswer(invocation -> {
                List<Member> list = invocation.getArgument(0);
                return list;
            });
            when(memberMapper.toResponse(any())).thenAnswer(invocation -> {
                Member gm = invocation.getArgument(0);
                return new MemberRes(gm.getId(), gm.getUserId(), gm.getRole(), gm.getStatus(), gm.getJoinedAt());
            });

            List<MemberRes> result = groupMemberService.addMembers(operatorId, groupId, req);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).joinedAt()).isNotNull();
            verify(memberRepository).saveAll(argThat(iterable -> {
                List<Member> list = new ArrayList<>();
                iterable.forEach(list::add);
                return list.size() == 1 && list.get(0).getJoinedAt() != null;
            }));
        }

        @Test
        @DisplayName("Khi operatorId khác null thì phải xác thực quyền Owner")
        void addMembers_WithOperatorId_VerifiesOwnerPermission() {
            UUID operatorId = UUID.randomUUID();
            UUID groupId = UUID.randomUUID();
            UUID user1 = UUID.randomUUID();
            Instant now = Instant.now();
            MemberAddReq req = new MemberAddReq(List.of(user1), MemberStatus.ACTIVE, MemberRole.MEMBER, now);

            when(memberRepository.findByGroupIdAndUserIdInAndStatusIn(eq(groupId), any(), any()))
                    .thenReturn(List.of());
            when(memberRepository.saveAll(any())).thenReturn(List.of());

            groupMemberService.addMembers(operatorId, groupId, req);

            verify(permissionValidator).verifyOwnerInGroupActive(groupId, operatorId);
        }
    }

    @Nested
    @DisplayName("findIdAllMember tests")
    class FindIdAllMemberTests {

        @Test
        @DisplayName("Trả về id người dùng của các thành viên ACTIVE, không phải id dòng thành viên")
        void findIdAllMember_ReturnsUserIdsNotMemberRowIds() {
            UUID groupId = UUID.randomUUID();
            UUID userA = UUID.randomUUID();
            UUID userB = UUID.randomUUID();
            Member memberA = Member.builder().id(UUID.randomUUID()).groupId(groupId).userId(userA)
                    .role(MemberRole.OWNER).status(MemberStatus.ACTIVE).joinedAt(Instant.now()).build();
            Member memberB = Member.builder().id(UUID.randomUUID()).groupId(groupId).userId(userB)
                    .role(MemberRole.MEMBER).status(MemberStatus.ACTIVE).joinedAt(Instant.now()).build();

            when(memberRepository.findByGroupIdAndStatusOrderByJoinedAtDesc(groupId, MemberStatus.ACTIVE))
                    .thenReturn(List.of(memberA, memberB));

            List<UUID> result = groupMemberService.findIdAllMember(groupId);

            assertThat(result).containsExactly(userA, userB);
            assertThat(result).doesNotContain(memberA.getId(), memberB.getId());
        }

        @Test
        @DisplayName("Nhóm không có thành viên ACTIVE thì trả về danh sách rỗng")
        void findIdAllMember_NoActiveMembers_ReturnsEmpty() {
            UUID groupId = UUID.randomUUID();
            when(memberRepository.findByGroupIdAndStatusOrderByJoinedAtDesc(groupId, MemberStatus.ACTIVE))
                    .thenReturn(List.of());

            assertThat(groupMemberService.findIdAllMember(groupId)).isEmpty();
        }
    }
}

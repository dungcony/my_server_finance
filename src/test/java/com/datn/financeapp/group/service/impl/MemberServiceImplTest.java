package com.datn.financeapp.group.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.request.member.MemberCreateReq;
import com.datn.financeapp.group.dto.response.member.MemberRes;
import com.datn.financeapp.group.entity.Member;
import com.datn.financeapp.group.enums.MemberRole;
import com.datn.financeapp.group.enums.MemberStatus;
import com.datn.financeapp.group.mapper.MemberMapper;
import com.datn.financeapp.group.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberServiceImplTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberMapper memberMapper;

    @InjectMocks
    private MemberServiceImpl groupMemberService;

    private final UUID groupId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Nested
    @DisplayName("create tests")
    class CreateTests {

        private void stubMapperAndSave(MemberCreateReq req) {
            when(memberMapper.toEntity(req)).thenReturn(Member.builder()
                    .groupId(groupId)
                    .userId(userId)
                    .role(req.role())
                    .status(req.status())
                    .build());
            when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));
            when(memberMapper.toResponse(any(Member.class))).thenAnswer(inv -> {
                Member m = inv.getArgument(0);
                return new MemberRes(m.getId(), m.getUserId(), m.getRole(), m.getStatus(), m.getJoinedAt());
            });
        }

        @Test
        @DisplayName("Không truyền role và status thì mặc định MEMBER, PENDING và chưa có joinedAt")
        void create_defaultsToMemberPending() {
            MemberCreateReq req = new MemberCreateReq(groupId, userId, null, null);
            stubMapperAndSave(req);

            MemberRes result = groupMemberService.create(req).orElseThrow();

            assertThat(result.id()).isNotNull();
            assertThat(result.role()).isEqualTo(MemberRole.MEMBER);
            assertThat(result.status()).isEqualTo(MemberStatus.PENDING);
            assertThat(result.joinedAt()).isNull();
        }

        @Test
        @DisplayName("Trạng thái ACTIVE thì ghi joinedAt và giữ nguyên role được truyền vào")
        void create_activeSetsJoinedAt() {
            MemberCreateReq req = new MemberCreateReq(groupId, userId, MemberRole.OWNER, MemberStatus.ACTIVE);
            stubMapperAndSave(req);

            MemberRes result = groupMemberService.create(req).orElseThrow();

            assertThat(result.role()).isEqualTo(MemberRole.OWNER);
            assertThat(result.status()).isEqualTo(MemberStatus.ACTIVE);
            assertThat(result.joinedAt()).isNotNull();
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
    @DisplayName("creates tests")
    class CreatesTests {

        @Test
        @DisplayName("Trả về danh sách rỗng khi input rỗng")
        void creates_emptyList_returnsEmpty() {
            List<MemberRes> result = groupMemberService.creates(List.of());
            assertThat(result).isEmpty();
            verify(memberRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("Tạo danh sách thành viên thành công")
        void creates_success() {
            UUID user1 = UUID.randomUUID();
            UUID user2 = UUID.randomUUID();
            MemberCreateReq req1 = new MemberCreateReq(groupId, user1, MemberRole.MEMBER, MemberStatus.ACTIVE);
            MemberCreateReq req2 = new MemberCreateReq(groupId, user2, MemberRole.MEMBER, MemberStatus.PENDING);

            when(memberMapper.toEntity(req1)).thenReturn(Member.builder().groupId(groupId).userId(user1).build());
            when(memberMapper.toEntity(req2)).thenReturn(Member.builder().groupId(groupId).userId(user2).build());

            when(memberRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
            when(memberMapper.toResponse(any(Member.class))).thenAnswer(inv -> {
                Member m = inv.getArgument(0);
                return new MemberRes(m.getId(), m.getUserId(), m.getRole(), m.getStatus(), m.getJoinedAt());
            });

            List<MemberRes> result = groupMemberService.creates(List.of(req1, req2));

            assertThat(result).hasSize(2);
            assertThat(result.get(0).joinedAt()).isNotNull();
            assertThat(result.get(1).joinedAt()).isNull();
        }
    }

    @Nested
    @DisplayName("countActiveMembers tests")
    class CountActiveMembersTests {

        @Test
        @DisplayName("Đếm số thành viên ACTIVE thành công")
        void countActiveMembers_success() {
            when(memberRepository.countByGroupIdAndStatus(groupId, MemberStatus.ACTIVE)).thenReturn(5L);
            long count = groupMemberService.countActiveMembers(groupId);
            assertThat(count).isEqualTo(5L);
        }
    }

    @Nested
    @DisplayName("getMember tests")
    class GetMemberTests {

        @Test
        @DisplayName("Lấy thành viên khi status null")
        void getMember_statusNull_success() {
            Member member = Member.builder().id(UUID.randomUUID()).groupId(groupId).userId(userId).build();
            MemberRes res = new MemberRes(member.getId(), userId, MemberRole.MEMBER, MemberStatus.ACTIVE, Instant.now());

            when(memberRepository.findByGroupIdAndUserId(groupId, userId)).thenReturn(Optional.of(member));
            when(memberMapper.toResponse(member)).thenReturn(res);

            MemberRes result = groupMemberService.getMember(groupId, userId, null);
            assertThat(result).isEqualTo(res);
        }

        @Test
        @DisplayName("Lấy thành viên với status cụ thể")
        void getMember_withStatus_success() {
            Member member = Member.builder().id(UUID.randomUUID()).groupId(groupId).userId(userId).status(MemberStatus.ACTIVE).build();
            MemberRes res = new MemberRes(member.getId(), userId, MemberRole.MEMBER, MemberStatus.ACTIVE, Instant.now());

            when(memberRepository.findByGroupIdAndUserIdAndStatus(groupId, userId, MemberStatus.ACTIVE))
                    .thenReturn(Optional.of(member));
            when(memberMapper.toResponse(member)).thenReturn(res);

            MemberRes result = groupMemberService.getMember(groupId, userId, MemberStatus.ACTIVE);
            assertThat(result).isEqualTo(res);
        }

        @Test
        @DisplayName("Ném FORBIDDEN_NOT_GROUP_MEMBER khi không tìm thấy")
        void getMember_notFound_throwsException() {
            when(memberRepository.findByGroupIdAndUserId(groupId, userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> groupMemberService.getMember(groupId, userId, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.FORBIDDEN_NOT_GROUP_MEMBER.getCode());
        }
    }

    @Nested
    @DisplayName("getActivateMembers & getMembersWithStatusIn tests")
    class GetMembersTests {

        @Test
        @DisplayName("Lấy danh sách thành viên ACTIVE")
        void getActivateMembers_success() {
            Member member = Member.builder().id(UUID.randomUUID()).groupId(groupId).userId(userId).build();
            MemberRes res = new MemberRes(member.getId(), userId, MemberRole.MEMBER, MemberStatus.ACTIVE, Instant.now());

            when(memberRepository.findAllByGroupIdAndStatus(groupId, MemberStatus.ACTIVE)).thenReturn(List.of(member));
            when(memberMapper.toResponse(member)).thenReturn(res);

            List<MemberRes> result = groupMemberService.getActivateMembers(groupId);
            assertThat(result).containsExactly(res);
        }

        @Test
        @DisplayName("Lấy danh sách thành viên theo nhiều status")
        void getMembersWithStatusIn_success() {
            Member member = Member.builder().id(UUID.randomUUID()).groupId(groupId).userId(userId).build();
            MemberRes res = new MemberRes(member.getId(), userId, MemberRole.MEMBER, MemberStatus.ACTIVE, Instant.now());
            List<MemberStatus> statuses = List.of(MemberStatus.ACTIVE, MemberStatus.PENDING);

            when(memberRepository.findAllByGroupIdAndStatusIn(groupId, statuses)).thenReturn(List.of(member));
            when(memberMapper.toResponse(member)).thenReturn(res);

            List<MemberRes> result = groupMemberService.getMembersWithStatusIn(groupId, statuses);
            assertThat(result).containsExactly(res);
        }
    }

    @Nested
    @DisplayName("findIdAllMember tests")
    class FindIdAllMemberTests {

        @Test
        @DisplayName("Trả về id người dùng của các thành viên ACTIVE, không phải id dòng thành viên")
        void findIdAllMember_ReturnsUserIdsNotMemberRowIds() {
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
            when(memberRepository.findByGroupIdAndStatusOrderByJoinedAtDesc(groupId, MemberStatus.ACTIVE))
                    .thenReturn(List.of());

            assertThat(groupMemberService.findIdAllMember(groupId)).isEmpty();
        }
    }

    @Nested
    @DisplayName("allMemberInGroup tests")
    class AllMemberInGroupTests {

        @Test
        @DisplayName("Kiểm tra allMemberInGroup ủy quyền đúng cho repository")
        void allMemberInGroup_success() {
            List<UUID> userIds = List.of(userId);
            when(memberRepository.allMemberInGroup(groupId, userIds)).thenReturn(true);

            boolean result = groupMemberService.allMemberInGroup(groupId, userIds);
            assertThat(result).isTrue();
            verify(memberRepository).allMemberInGroup(groupId, userIds);
        }
    }

    @Nested
    @DisplayName("assertNotInGroup tests")
    class AssertNotInGroupTests {

        private final List<MemberStatus> currentStatuses = List.of(MemberStatus.ACTIVE, MemberStatus.PENDING);

        @Test
        @DisplayName("Chưa có dòng ACTIVE/PENDING nào thì không ném lỗi, kể cả khi từng rời nhóm")
        void assertNotInGroup_noCurrentRow_passes() {
            when(memberRepository.findByGroupIdAndUserIdAndStatusIn(groupId, userId, currentStatuses))
                    .thenReturn(Optional.empty());

            assertThatCode(() -> groupMemberService.assertNotInGroup(groupId, userId))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Đang ACTIVE thì ném ALREADY_IN_GROUP")
        void assertNotInGroup_active_throwsAlreadyInGroup() {
            Member active = Member.builder().id(UUID.randomUUID()).groupId(groupId).userId(userId)
                    .status(MemberStatus.ACTIVE).build();
            when(memberRepository.findByGroupIdAndUserIdAndStatusIn(groupId, userId, currentStatuses))
                    .thenReturn(Optional.of(active));

            assertThatThrownBy(() -> groupMemberService.assertNotInGroup(groupId, userId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.ALREADY_IN_GROUP.getCode());
        }

        @Test
        @DisplayName("Đang PENDING thì ném PENDING_IN_GROUP")
        void assertNotInGroup_pending_throwsPendingInGroup() {
            Member pending = Member.builder().id(UUID.randomUUID()).groupId(groupId).userId(userId)
                    .status(MemberStatus.PENDING).build();
            when(memberRepository.findByGroupIdAndUserIdAndStatusIn(groupId, userId, currentStatuses))
                    .thenReturn(Optional.of(pending));

            assertThatThrownBy(() -> groupMemberService.assertNotInGroup(groupId, userId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.PENDING_IN_GROUP.getCode());
        }
    }
}

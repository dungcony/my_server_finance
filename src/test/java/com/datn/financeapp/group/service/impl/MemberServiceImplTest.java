package com.datn.financeapp.group.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

            when(memberRepository.save(any(Member.class))).thenReturn(savedMember);
            when(memberMapper.toResponse(savedMember)).thenReturn(expectedRes);

            MemberRes result = groupMemberService.addMember(userId, groupId, MemberStatus.ACTIVE, now);

            assertThat(result).isNotNull();
            assertThat(result.userId()).isEqualTo(userId);
            assertThat(result.role()).isEqualTo(MemberRole.MEMBER);
            verify(memberRepository).save(argThat(m ->
                    m.getGroupId().equals(groupId)
                            && m.getUserId().equals(userId)
                            && m.getRole() == MemberRole.MEMBER
                            && m.getStatus() == MemberStatus.ACTIVE
                            && m.getJoinedAt().equals(now)
            ));
        }
    }

    @Nested
    @DisplayName("addMembers tests")
    class AddMembersTests {

        @Test
        @DisplayName("Trả về danh sách rỗng khi input null hoặc rỗng")
        void addMembers_NullOrEmptyList_ReturnsEmpty() {
            UUID operatorId = UUID.randomUUID();
            UUID groupId = UUID.randomUUID();
            Instant now = Instant.now();

            List<MemberRes> resNull = groupMemberService.addMembers(operatorId, groupId, null, now);
            List<MemberRes> resEmpty = groupMemberService.addMembers(operatorId, groupId, List.of(), now);

            assertThat(resNull).isEmpty();
            assertThat(resEmpty).isEmpty();
            verify(memberRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("Trả về danh sách rỗng khi input chỉ chứa phần tử null")
        void addMembers_OnlyNullElements_ReturnsEmpty() {
            UUID operatorId = UUID.randomUUID();
            UUID groupId = UUID.randomUUID();
            Instant now = Instant.now();

            List<UUID> input = new ArrayList<>();
            input.add(null);
            input.add(null);

            List<MemberRes> res = groupMemberService.addMembers(operatorId, groupId, input, now);

            assertThat(res).isEmpty();
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

            when(memberRepository.saveAll(any())).thenReturn(savedMembers);
            when(memberMapper.toResponse(member1)).thenReturn(res1);
            when(memberMapper.toResponse(member2)).thenReturn(res2);

            UUID operatorId = UUID.randomUUID();
            List<MemberRes> result = groupMemberService.addMembers(operatorId, groupId, input, now);

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
        @DisplayName("Thêm danh sách khi now là null thì tự gán Instant hiện tại")
        void addMembers_WithNullNow_UsesCurrentInstant() {
            UUID operatorId = UUID.randomUUID();
            UUID groupId = UUID.randomUUID();
            UUID user1 = UUID.randomUUID();

            when(memberRepository.saveAll(any())).thenAnswer(invocation -> {
                List<Member> list = invocation.getArgument(0);
                return list;
            });
            when(memberMapper.toResponse(any())).thenAnswer(invocation -> {
                Member gm = invocation.getArgument(0);
                return new MemberRes(gm.getId(), gm.getUserId(), gm.getRole(), gm.getStatus(), gm.getJoinedAt());
            });

            List<MemberRes> result = groupMemberService.addMembers(operatorId, groupId, List.of(user1), null);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).joinedAt()).isNotNull();
            verify(memberRepository).saveAll(argThat(iterable -> {
                List<Member> list = new ArrayList<>();
                iterable.forEach(list::add);
                return list.size() == 1 && list.get(0).getJoinedAt() != null;
            }));
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

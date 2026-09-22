package com.datn.financeapp.group.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datn.financeapp.group.dto.response.group.GroupMemberRes;
import com.datn.financeapp.group.entity.GroupMember;
import com.datn.financeapp.group.enums.GroupRole;
import com.datn.financeapp.group.enums.MemberStatus;
import com.datn.financeapp.group.mapper.GroupMemberMapper;
import com.datn.financeapp.group.repository.GroupMemberRepository;
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
class GroupMemberServiceImplTest {

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private GroupMemberMapper groupMemberMapper;

    @InjectMocks
    private GroupMemberServiceImpl groupMemberService;

    @Nested
    @DisplayName("addMember tests")
    class AddMemberTests {

        @Test
        @DisplayName("Thêm 1 thành viên thành công")
        void addMember_Success() {
            UUID groupId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Instant now = Instant.now();

            GroupMember savedMember = GroupMember.builder()
                    .id(UUID.randomUUID())
                    .groupId(groupId)
                    .userId(userId)
                    .role(GroupRole.MEMBER)
                    .status(MemberStatus.ACTIVE)
                    .joinedAt(now)
                    .build();

            GroupMemberRes expectedRes = new GroupMemberRes(
                    savedMember.getId(),
                    userId,
                    GroupRole.MEMBER,
                    MemberStatus.ACTIVE,
                    now
            );

            when(groupMemberRepository.save(any(GroupMember.class))).thenReturn(savedMember);
            when(groupMemberMapper.toResponse(savedMember)).thenReturn(expectedRes);

            GroupMemberRes result = groupMemberService.addMember(groupId, userId, GroupRole.MEMBER, now);

            assertThat(result).isNotNull();
            assertThat(result.userId()).isEqualTo(userId);
            assertThat(result.role()).isEqualTo(GroupRole.MEMBER);
            verify(groupMemberRepository).save(argThat(m ->
                    m.getGroupId().equals(groupId)
                            && m.getUserId().equals(userId)
                            && m.getRole() == GroupRole.MEMBER
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
            UUID groupId = UUID.randomUUID();
            Instant now = Instant.now();

            List<GroupMemberRes> resNull = groupMemberService.addMembers(groupId, null, now);
            List<GroupMemberRes> resEmpty = groupMemberService.addMembers(groupId, List.of(), now);

            assertThat(resNull).isEmpty();
            assertThat(resEmpty).isEmpty();
            verify(groupMemberRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("Trả về danh sách rỗng khi input chỉ chứa phần tử null")
        void addMembers_OnlyNullElements_ReturnsEmpty() {
            UUID groupId = UUID.randomUUID();
            Instant now = Instant.now();

            List<UUID> input = new ArrayList<>();
            input.add(null);
            input.add(null);

            List<GroupMemberRes> res = groupMemberService.addMembers(groupId, input, now);

            assertThat(res).isEmpty();
            verify(groupMemberRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("Thêm danh sách thành viên thành công, tự động loại bỏ trùng lặp và null")
        void addMembers_Success_DeduplicatesAndFiltersNulls() {
            UUID groupId = UUID.randomUUID();
            UUID user1 = UUID.randomUUID();
            UUID user2 = UUID.randomUUID();
            Instant now = Instant.now();

            List<UUID> input = Arrays.asList(user1, user2, user1, null);

            GroupMember member1 = GroupMember.builder()
                    .id(UUID.randomUUID())
                    .groupId(groupId)
                    .userId(user1)
                    .role(GroupRole.MEMBER)
                    .status(MemberStatus.ACTIVE)
                    .joinedAt(now)
                    .build();

            GroupMember member2 = GroupMember.builder()
                    .id(UUID.randomUUID())
                    .groupId(groupId)
                    .userId(user2)
                    .role(GroupRole.MEMBER)
                    .status(MemberStatus.ACTIVE)
                    .joinedAt(now)
                    .build();

            List<GroupMember> savedMembers = List.of(member1, member2);

            GroupMemberRes res1 = new GroupMemberRes(member1.getId(), user1, GroupRole.MEMBER, MemberStatus.ACTIVE, now);
            GroupMemberRes res2 = new GroupMemberRes(member2.getId(), user2, GroupRole.MEMBER, MemberStatus.ACTIVE, now);

            when(groupMemberRepository.saveAll(any())).thenReturn(savedMembers);
            when(groupMemberMapper.toResponse(member1)).thenReturn(res1);
            when(groupMemberMapper.toResponse(member2)).thenReturn(res2);

            List<GroupMemberRes> result = groupMemberService.addMembers(groupId, input, now);

            assertThat(result).hasSize(2);
            assertThat(result).containsExactly(res1, res2);

            verify(groupMemberRepository).saveAll(argThat(iterable -> {
                List<GroupMember> list = new ArrayList<>();
                iterable.forEach(list::add);
                return list.size() == 2
                        && list.stream().anyMatch(m -> m.getUserId().equals(user1) && m.getRole() == GroupRole.MEMBER)
                        && list.stream().anyMatch(m -> m.getUserId().equals(user2) && m.getRole() == GroupRole.MEMBER);
            }));
        }

        @Test
        @DisplayName("Thêm danh sách khi now là null thì tự gán Instant hiện tại")
        void addMembers_WithNullNow_UsesCurrentInstant() {
            UUID groupId = UUID.randomUUID();
            UUID user1 = UUID.randomUUID();

            when(groupMemberRepository.saveAll(any())).thenAnswer(invocation -> {
                List<GroupMember> list = invocation.getArgument(0);
                return list;
            });
            when(groupMemberMapper.toResponse(any())).thenAnswer(invocation -> {
                GroupMember gm = invocation.getArgument(0);
                return new GroupMemberRes(gm.getId(), gm.getUserId(), gm.getRole(), gm.getStatus(), gm.getJoinedAt());
            });

            List<GroupMemberRes> result = groupMemberService.addMembers(groupId, List.of(user1), null);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).joinedAt()).isNotNull();
            verify(groupMemberRepository).saveAll(argThat(iterable -> {
                List<GroupMember> list = new ArrayList<>();
                iterable.forEach(list::add);
                return list.size() == 1 && list.get(0).getJoinedAt() != null;
            }));
        }
    }
}

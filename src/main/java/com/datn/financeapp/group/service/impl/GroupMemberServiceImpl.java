package com.datn.financeapp.group.service.impl;

import com.datn.financeapp.group.dto.response.group.GroupMemberRes;
import com.datn.financeapp.group.entity.GroupMember;
import com.datn.financeapp.group.enums.GroupRole;
import com.datn.financeapp.group.enums.MemberStatus;
import com.datn.financeapp.group.mapper.GroupMemberMapper;
import com.datn.financeapp.group.repository.GroupMemberRepository;
import com.datn.financeapp.group.service.GroupMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupMemberServiceImpl implements GroupMemberService {

    private final GroupMemberRepository groupMemberRepository;
    private final GroupMemberMapper groupMemberMapper;

    @Override
    @Transactional
    public GroupMemberRes addMember(UUID groupId, UUID userId, GroupRole role, Instant now) {
        Instant joinedAt = now != null ? now : Instant.now();
        GroupMember member = buildGroupMember(groupId, userId, role, joinedAt);

        return groupMemberMapper
                .toResponse(groupMemberRepository.save(member));
    }

    @Override
    @Transactional
    public List<GroupMemberRes> addMembers(UUID groupId, List<UUID> userId, Instant now) {
        if (userId == null || userId.isEmpty()) {
            return List.of();
        }

        Instant joinedAt = now != null ? now : Instant.now();

        List<GroupMember> members = userId.stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(uid -> buildGroupMember(groupId, uid, GroupRole.MEMBER, joinedAt))
                .toList();

        if (members.isEmpty()) {
            return List.of();
        }

        return groupMemberRepository.saveAll(members)
                .stream()
                .map(groupMemberMapper::toResponse)
                .toList();
    }

    @Override
    public long countActiveMembers(UUID groupId) {
        return groupMemberRepository.countByGroupIdAndStatus(groupId, MemberStatus.ACTIVE);
    }

    @Override
    public GroupMemberRes findMemberById(UUID groupId, UUID userId) {
        return groupMemberRepository.findByGroupIdAndUserIdAndStatusIn(groupId, userId, List.of(MemberStatus.ACTIVE))
                .map(groupMemberMapper::toResponse)
                .orElse(null);
    }

    private GroupMember buildGroupMember(UUID groupId, UUID userId, GroupRole role, Instant joinedAt) {
        return GroupMember.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .userId(userId)
                .role(role)
                .status(MemberStatus.ACTIVE)
                .joinedAt(joinedAt)
                .build();
    }
}

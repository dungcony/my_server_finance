package com.datn.financeapp.group.service;

import com.datn.financeapp.group.dto.response.group.GroupMemberRes;
import com.datn.financeapp.group.enums.GroupRole;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface GroupMemberService {
    GroupMemberRes addMember(
            UUID groupId,
            UUID userId,
            GroupRole role,
            Instant now
    );

    List<GroupMemberRes> addMembers(
            UUID groupId,
            List<UUID> userId,
            Instant now
    );

    long countActiveMembers(UUID groupId);
    GroupMemberRes findMemberById(UUID groupId, UUID userId);
}

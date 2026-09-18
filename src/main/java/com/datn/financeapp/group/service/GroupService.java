package com.datn.financeapp.group.service;

import com.datn.financeapp.group.dto.group.GroupCreateReq;
import com.datn.financeapp.group.dto.group.GroupDetailRes;
import com.datn.financeapp.group.dto.group.GroupInviteCodeRes;
import com.datn.financeapp.group.dto.group.GroupJoinReq;
import com.datn.financeapp.group.dto.group.GroupMemberRes;
import com.datn.financeapp.group.dto.group.GroupSummaryRes;
import com.datn.financeapp.group.dto.group.GroupUpdateReq;
import com.datn.financeapp.group.enums.GroupRole;
import java.util.List;
import java.util.UUID;

public interface GroupService {

    GroupDetailRes create(UUID userId, GroupCreateReq req);

    List<GroupSummaryRes> list(UUID userId);

    GroupDetailRes detail(UUID userId, UUID groupId);

    GroupDetailRes update(UUID userId, UUID groupId, GroupUpdateReq req);

    void archive(UUID userId, UUID groupId);

    void unarchive(UUID userId, UUID groupId);

    void transferOwnership(UUID userId, UUID groupId, UUID newOwnerUserId);

    void delete(UUID userId, UUID groupId);

    GroupInviteCodeRes getInviteCode(UUID userId, UUID groupId);

    GroupInviteCodeRes regenerateInviteCode(UUID userId, UUID groupId);

    GroupDetailRes join(UUID userId, GroupJoinReq req);

    List<GroupMemberRes> listMembers(UUID userId, UUID groupId);

    void approveMember(UUID userId, UUID groupId, UUID memberUserId);

    void updateMemberRole(UUID userId, UUID groupId, UUID memberUserId, GroupRole role);

    void removeMember(UUID userId, UUID groupId, UUID memberUserId);

    void leave(UUID userId, UUID groupId);
}

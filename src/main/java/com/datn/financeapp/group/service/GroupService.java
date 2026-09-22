package com.datn.financeapp.group.service;

import com.datn.financeapp.group.dto.request.group.GroupCreateReq;
import com.datn.financeapp.group.dto.response.group.GroupDetailRes;
import com.datn.financeapp.group.dto.response.group.GroupInviteCodeRes;
import com.datn.financeapp.group.dto.request.group.GroupJoinReq;
import com.datn.financeapp.group.dto.response.group.GroupMemberRes;
import com.datn.financeapp.group.dto.response.group.GroupSummaryRes;
import com.datn.financeapp.group.dto.request.group.GroupUpdateReq;
import com.datn.financeapp.group.enums.GroupRole;
import java.util.List;
import java.util.UUID;

public interface GroupService {

    GroupDetailRes findNotDeletedById(UUID id);

    default GroupDetailRes findById(UUID id) {
        return findNotDeletedById(id);
    }

    GroupDetailRes create(UUID userId, GroupCreateReq req);

    List<GroupSummaryRes> groupsByUser(UUID userId);

    default List<GroupSummaryRes> list(UUID userId) {
        return groupsByUser(userId);
    }

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

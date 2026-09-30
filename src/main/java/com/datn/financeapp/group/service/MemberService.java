package com.datn.financeapp.group.service;

import com.datn.financeapp.group.dto.request.member.MemberAddReq;
import com.datn.financeapp.group.dto.response.member.MemberRes;
import com.datn.financeapp.group.enums.MemberStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service quản lý thành viên trong nhóm tài chính.
 * <p>
 * Các hàm trong interface:
 * <ul>
 * <li>{@link #addOwner}: Thêm trưởng nhóm. Input: memberId, groupId,  now.
 * Output: MemberRes.</li>
 * <li>{@link #addMembers}: Thêm nhiều thành viên. Input: operatorId, groupId,
 * memberIds, now. Output: danh sách MemberRes.</li>
 * <li>{@link #countActiveMembers}: Đếm thành viên active. Input: groupId.
 * Output: số lượng (long).</li>
 * </ul>
 * </p>
 */
public interface MemberService {
    MemberRes addOwner(UUID memberId, UUID groupId, Instant now);

    MemberRes addMember(UUID memberId, UUID groupId, MemberStatus status, Instant now);

    List<MemberRes> addMembers(UUID operatorId, UUID groupId, MemberAddReq req);

    long countActiveMembers(UUID groupId);

    MemberRes getMember(UUID groupId, UUID memberId, MemberStatus status);

    default MemberRes getMember(UUID groupId, UUID memberId) {
        return getMember(groupId, memberId, null);
    }

    List<MemberRes> findMembers(UUID groupId, List<UUID> memberIds, MemberStatus memberStatus);

    default List<MemberRes> findMembers(UUID groupId, List<UUID> memberIds) {
        return findMembers(groupId, memberIds, null);
    }

    default List<MemberRes> findMembers(UUID groupId) {
        return findMembers(groupId, null, null);
    }

    default List<MemberRes> findMembers(UUID groupId, MemberStatus memberStatus) {
        return findMembers(groupId, null, memberStatus);
    }

    // danh sách id người dùng (userId) của các thành viên ACTIVE trong nhóm
    List<UUID> findIdAllMember(UUID groupId);

    boolean allMemberInGroup(UUID groupId, List<UUID> memberIds);
}

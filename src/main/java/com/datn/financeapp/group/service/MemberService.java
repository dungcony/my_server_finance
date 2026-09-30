package com.datn.financeapp.group.service;

import com.datn.financeapp.group.dto.request.member.MemberAddReq;
import com.datn.financeapp.group.dto.request.member.MemberCreateReq;
import com.datn.financeapp.group.dto.response.member.MemberRes;
import com.datn.financeapp.group.entity.Member;
import com.datn.financeapp.group.enums.MemberStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service quản lý thành viên trong nhóm tài chính.
 * <p>
 * Các hàm trong interface:
 * <ul>
 * <li>{@link #create(MemberCreateReq)}: Tạo 1 bản ghi thành viên.</li>
 * <li>{@link #creates(List)} : Tạo 1 danh sách bản ghi thành viên.</li>
 * <li>{@link #getActivateMembers(UUID)} : Lấy danh sách user active của group.</li>
 * <li>{@link #getMembersWithStatusIn(UUID, List)}  Lấy danh sách user có 1 trong các trạng thái của group.</li>
 * <li>{@link #findIdAllMember(UUID)}  Lấy danh sách id active của group.</li>
 * <li>{@link #countActiveMembers}: Đếm thành viên active. Input: groupId.</li>
 * <li>{@link #allMemberInGroup(UUID, List)}: kiểm tra tất cả mem đều thuộc group
 * Output: số lượng (long).</li>
 * </ul>
 * </p>
 */
public interface MemberService {

    Optional<MemberRes> create(MemberCreateReq req);

    List<MemberRes> creates(List<MemberCreateReq> req);

    long countActiveMembers(UUID groupId);

    MemberRes getMember(UUID groupId, UUID memberId, MemberStatus status);

    List<MemberRes> getActivateMembers(UUID groupId);

    List<MemberRes> getMembersWithStatusIn(UUID groupId, List<MemberStatus> statuses);

    // danh sách id người dùng (userId) của các thành viên ACTIVE trong nhóm
    List<UUID> findIdAllMember(UUID groupId);

    boolean allMemberInGroup(UUID groupId, List<UUID> memberIds);
}

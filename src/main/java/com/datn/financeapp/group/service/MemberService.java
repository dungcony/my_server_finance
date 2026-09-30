package com.datn.financeapp.group.service;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.group.dto.response.member.MemberRes;
import com.datn.financeapp.group.entity.Member;
import com.datn.financeapp.group.enums.MemberStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service quản lý thành viên trong nhóm tài chính.
 * <p>
 * Các hàm trong interface:
 * <ul>
 *   <li>{@link #addOwner}: Thêm trưởng nhóm. Input: groupId, memberId, now. Output: MemberRes.</li>
 *   <li>{@link #addMember}: Thêm 1 thành viên. Input: memberId, groupId, status, now. Output: MemberRes.</li>
 *   <li>{@link #addMembers}: Thêm nhiều thành viên. Input: operatorId, groupId, memberIds, now. Output: danh sách MemberRes.</li>
 *   <li>{@link #countActiveMembers}: Đếm thành viên active. Input: groupId. Output: số lượng (long).</li>
 *   <li>{@link #findMember}, {@link #findMembers}: Tra cứu thành viên. Input: groupId, memberId(s), status... Output: MemberRes hoặc danh sách MemberRes/GroupMember.</li>
 *   <li>{@link #leave}: Rời nhóm. Input: operatorId, groupId. Output: void.</li>
 *   <li>{@link #approve}: Duyệt 1 thành viên. Input: operatorId, groupId, memberId. Output: void.</li>
 *   <li>{@link #approveAll}: Duyệt tất cả. Input: operatorId, groupId. Output: số lượng được duyệt (int).</li>
 *   <li>{@link #removeMember}: Xóa/Mời ra khỏi nhóm. Input: operatorId, groupId, memberId. Output: void.</li>
 * </ul>
 * </p>
 */
public interface MemberService {
    MemberRes addOwner(
            UUID groupId,
            UUID memberId,
            Instant now
    );

    MemberRes addMember(
            UUID memberId,
            UUID groupId,
            MemberStatus memberStatus,
            Instant now
    );

    List<MemberRes> addMembers(
            UUID operatorId,
            UUID groupId,
            List<UUID> memberIds,
            Instant now
    );

    long countActiveMembers(UUID groupId);

    MemberRes findMember(UUID groupId, UUID memberId);

    MemberRes findMember(UUID groupId, UUID memberId, MemberStatus status);

    List<Member> findMembers(UUID groupId, List<UUID> memberIds);

    List<MemberRes> findMembers(UUID groupId);

    List<MemberRes> findMembers(UUID groupId, MemberStatus memberStatus);

    // danh sách id người dùng (userId) của các thành viên ACTIVE trong nhóm
    List<UUID> findIdAllMember(UUID groupId);

    boolean allMemberInGroup(UUID groupId, List<UUID> memberIds);

    void leave(UUID operatorId, UUID groupId);

    /**
     * Duyệt một thành viên đang chờ duyệt ({@code PENDING}) vào nhóm chính thức ({@code ACTIVE}).
     * <p>Yêu cầu quyền: Trưởng nhóm ({@code OWNER}).</p>
     *
     * @param operatorId ID người thực hiện (phải là Owner)
     * @param groupId    ID nhóm
     * @param memberId   ID thành viên cần được duyệt
     * @throws BusinessException nếu không phải Owner ({@code FORBIDDEN_OWNER_REQUIRED})
     *                           hoặc thành viên không ở trạng thái PENDING
     */
    void approve(UUID operatorId, UUID groupId, UUID memberId);

    /**
     * Duyệt một thành viên đang chờ duyệt ({@code PENDING}) vào nhóm chính thức ({@code ACTIVE}).
     * <p>Yêu cầu quyền: Trưởng nhóm ({@code OWNER}).</p>
     *
     * @param operatorId ID người thực hiện (phải là Owner)
     * @param groupId    ID nhóm
     * @throws BusinessException nếu không phải Owner ({@code FORBIDDEN_OWNER_REQUIRED})
     *                           hoặc thành viên không ở trạng thái PENDING
     */
    int approveAll(UUID operatorId, UUID groupId);

    /**
     * Xóa / Mời một thành viên rời khỏi nhóm (chuyển trạng thái sang {@code REMOVED}).
     * <p>
     * Yêu cầu quyền: Trưởng nhóm ({@code OWNER}).
     * Không được phép xóa Owner duy nhất. Nếu người bị xóa là thủ quỹ đang giữ quỹ, quỹ sẽ tự động bàn giao lại cho Owner.
     * </p>
     *
     * @param operatorId ID người thực hiện (phải là Owner)
     * @param groupId    ID nhóm
     * @param memberId   ID thành viên bị xóa
     * @throws BusinessException nếu không phải Owner, cố xóa Owner duy nhất,
     *                           hoặc nhóm còn giao dịch chờ duyệt
     */
    void removeMember(UUID operatorId, UUID groupId, UUID memberId);
}

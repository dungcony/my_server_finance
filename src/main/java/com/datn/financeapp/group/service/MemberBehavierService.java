package com.datn.financeapp.group.service;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.group.dto.request.member.MemberAddReq;
import com.datn.financeapp.group.dto.response.member.MemberRes;
import com.datn.financeapp.group.enums.MemberStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service quản lý hành vi thành viên trong nhóm tài chính.
 * <p>
 * Các hàm trong interface:
 * <ul>
 * <li>{@link #leave}: Rời nhóm. Input: operatorId, groupId. Output: void.</li>
 * <li>{@link #approve}: Duyệt 1 thành viên. Input: operatorId, groupId, memberId. Output: void.</li>
 * <li>{@link #approveAll}: Duyệt tất cả. Input: operatorId, groupId. Output: số lượng được duyệt (int).</li>
 * <li>{@link #reject(UUID, UUID, UUID)}: Từ chối 1 thành viên Input: operatorId, groupId. Output: void
 * <li>{@link #rejectAll(UUID, UUID)}: Từ chối tất cả. Input: operatorId, groupId. Output: số lượng bị từ chối (int).</li>
 * <li>{@link #removeMember}: Xóa/Mời ra khỏi nhóm. Input: operatorId, groupId,memberId. Output: void.</li>
 * <li>{@link #transferOwnership(UUID, UUID, UUID)}: chuyển quyền trưởng nhóm Input: operatorId, groupId, memberId. Output: void.</li>
 * </ul>
 * </p>
 */
public interface MemberBehavierService {


    MemberRes ownerAddMember(UUID memberId, UUID groupId);

    List<MemberRes> ownerAddMembers(UUID operatorId, UUID groupId, MemberAddReq req);

    void leave(UUID operatorId, UUID groupId);

    /**
     * Duyệt một thành viên đang chờ duyệt ({@code PENDING}) vào nhóm chính thức
     * ({@code ACTIVE}).
     * <p>
     * Yêu cầu quyền: Trưởng nhóm ({@code OWNER}).
     * </p>
     *
     * @param operatorId ID người thực hiện (phải là Owner)
     * @param groupId    ID nhóm
     * @param memberId   ID thành viên cần được duyệt
     * @throws BusinessException nếu không phải Owner
     *                           ({@code FORBIDDEN_OWNER_REQUIRED})
     *                           hoặc thành viên không ở trạng thái PENDING
     */
    void approve(UUID operatorId, UUID groupId, UUID memberId);

    /**
     * Duyệt toàn bộ thành viên đang chờ duyệt ({@code PENDING}) vào nhóm chính thức
     * ({@code ACTIVE}).
     * <p>
     * Yêu cầu quyền: Trưởng nhóm ({@code OWNER}).
     * </p>
     *
     * @param operatorId ID người thực hiện (phải là Owner)
     * @param groupId    ID nhóm
     * @throws BusinessException nếu không phải Owner
     *                           ({@code FORBIDDEN_OWNER_REQUIRED})
     *                           hoặc thành viên không ở trạng thái PENDING
     */
    int approveAll(UUID operatorId, UUID groupId);

    /**
     * Xóa một thành viên đang chờ duyệt ({@code PENDING}) vào nhóm chính thức
     * ({@code ACTIVE}).
     * <p>
     * Yêu cầu quyền: Trưởng nhóm ({@code OWNER}).
     * </p>
     *
     * @param operatorId ID người thực hiện (phải là Owner)
     * @param groupId    ID nhóm
     * @throws BusinessException nếu không phải Owner
     *                           ({@code FORBIDDEN_OWNER_REQUIRED})
     *                           hoặc thành viên không ở trạng thái PENDING
     */
    void reject(UUID operatorId, UUID groupId, UUID memberId);

    /**
     * Xóa toàn bộ thành viên đang chờ duyệt ({@code PENDING}) vào nhóm chính thức
     * ({@code ACTIVE}).
     * <p>
     * Yêu cầu quyền: Trưởng nhóm ({@code OWNER}).
     * </p>
     *
     * @param operatorId ID người thực hiện (phải là Owner)
     * @param groupId    ID nhóm
     * @return số thành viên đã bị từ chối (0 nếu không có ai đang chờ duyệt)
     * @throws BusinessException nếu không phải Owner
     *                           ({@code FORBIDDEN_OWNER_REQUIRED})
     */
    int rejectAll(UUID operatorId, UUID groupId);

    /**
     * Xóa / Mời một thành viên rời khỏi nhóm (chuyển trạng thái sang
     * {@code REMOVED}).
     * <p>
     * Yêu cầu quyền: Trưởng nhóm ({@code OWNER}).
     * Không được phép xóa Owner duy nhất. Nếu người bị xóa là thủ quỹ đang giữ quỹ,
     * quỹ sẽ tự động bàn giao lại cho Owner.
     * </p>
     *
     * @param operatorId ID người thực hiện (phải là Owner)
     * @param groupId    ID nhóm
     * @param memberId   ID thành viên bị xóa
     * @throws BusinessException nếu không phải Owner, cố xóa Owner duy nhất,
     *                           hoặc nhóm còn giao dịch chờ duyệt
     */
    void removeMember(UUID operatorId, UUID groupId, UUID memberId);

    /**
     * Chuyển giao quyền Trưởng nhóm ({@code OWNER}) cho một thành viên khác trong nhóm.
     * <p>
     * Yêu cầu: Người gọi phải là Trưởng nhóm hiện tại, và người nhận quyền phải là thành viên
     * đang hoạt động ({@code ACTIVE}) của nhóm.
     * </p>
     *
     * @param operatorId ID Trưởng nhóm hiện tại
     * @param groupId    ID nhóm
     * @param memberId   ID thành viên được chỉ định làm Trưởng nhóm mới
     * @throws BusinessException nếu không phải Owner, chuyển cho chính mình,
     *                           hoặc người nhận không phải thành viên hợp lệ
     */
    void transferOwnership(UUID operatorId, UUID groupId, UUID memberId);

}

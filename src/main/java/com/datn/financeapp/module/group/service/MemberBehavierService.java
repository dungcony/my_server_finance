package com.datn.financeapp.module.group.service;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.module.group.dto.request.member.MemberAddReq;
import com.datn.financeapp.module.group.dto.response.member.MemberRes;
import com.datn.financeapp.module.group.enums.MemberStatus;

import java.util.List;
import java.util.UUID;

/**
 * Service quản lý hành vi thành viên trong nhóm tài chính.
 */
public interface MemberBehavierService {


    /**
     * Danh sách thành viên của nhóm kèm tên hiển thị và cờ thủ quỹ.
     * <p>
     * Yêu cầu: người gọi là thành viên {@code ACTIVE}. Lọc {@code PENDING} chỉ dành cho Trưởng nhóm.
     * Bỏ trống {@code status}: Trưởng nhóm thấy {@code ACTIVE} + {@code PENDING}, thành viên thường chỉ thấy
     * {@code ACTIVE}.
     * </p>
     *
     * @param groupId ID nhóm
     * @param status  trạng thái cần lọc, có thể null
     * @throws BusinessException nếu lọc {@code PENDING} mà không phải Owner ({@code GROUP_OWNER_REQUIRED})
     */
    List<MemberRes> listMembers(UUID groupId, MemberStatus status);

    void ownerAddMember(UUID memberId, UUID groupId);

    List<MemberRes> ownerAddMembers(UUID groupId, MemberAddReq req);

    void leave(UUID groupId);

    /**
     * Duyệt một thành viên đang chờ duyệt ({@code PENDING}) vào nhóm chính thức
     * ({@code ACTIVE}).
     * <p>
     * Yêu cầu quyền: Trưởng nhóm ({@code OWNER}).
     * </p>
     *
     * @param groupId  ID nhóm
     * @param memberId ID thành viên cần được duyệt
     * @throws BusinessException nếu không phải Owner
     *                           ({@code GROUP_OWNER_REQUIRED})
     *                           hoặc thành viên không ở trạng thái PENDING
     */
    void approve(UUID groupId, UUID memberId);

    /**
     * Duyệt toàn bộ thành viên đang chờ duyệt ({@code PENDING}) vào nhóm chính thức
     * ({@code ACTIVE}).
     * <p>
     * Yêu cầu quyền: Trưởng nhóm ({@code OWNER}).
     * </p>
     *
     * @param groupId ID nhóm
     * @throws BusinessException nếu không phải Owner
     *                           ({@code GROUP_OWNER_REQUIRED})
     *                           hoặc thành viên không ở trạng thái PENDING
     */
    int approveAll(UUID groupId);

    /**
     * Xóa một thành viên đang chờ duyệt ({@code PENDING}) vào nhóm chính thức
     * ({@code ACTIVE}).
     * <p>
     * Yêu cầu quyền: Trưởng nhóm ({@code OWNER}).
     * </p>
     *
     * @param groupId ID nhóm
     * @throws BusinessException nếu không phải Owner
     *                           ({@code GROUP_OWNER_REQUIRED})
     *                           hoặc thành viên không ở trạng thái PENDING
     */
    void reject(UUID groupId, UUID memberId);

    /**
     * Xóa toàn bộ thành viên đang chờ duyệt ({@code PENDING}) vào nhóm chính thức
     * ({@code ACTIVE}).
     * <p>
     * Yêu cầu quyền: Trưởng nhóm ({@code OWNER}).
     * </p>
     *
     * @param groupId ID nhóm
     * @return số thành viên đã bị từ chối (0 nếu không có ai đang chờ duyệt)
     * @throws BusinessException nếu không phải Owner
     *                           ({@code GROUP_OWNER_REQUIRED})
     */
    int rejectAll(UUID groupId);

    /**
     * Xóa / Mời một thành viên rời khỏi nhóm (chuyển trạng thái sang
     * {@code REMOVED}).
     * <p>
     * Yêu cầu quyền: Trưởng nhóm ({@code OWNER}).
     * Không được phép xóa Owner duy nhất. Nếu người bị xóa là thủ quỹ đang giữ quỹ,
     * quỹ sẽ tự động bàn giao lại cho Owner.
     * </p>
     *
     * @param groupId  ID nhóm
     * @param memberId ID thành viên bị xóa
     * @throws BusinessException nếu không phải Owner, cố xóa Owner duy nhất,
     *                           hoặc nhóm còn giao dịch chờ duyệt
     */
    void removeMember(UUID groupId, UUID memberId);

    /**
     * Chuyển giao quyền Trưởng nhóm ({@code OWNER}) cho một thành viên khác trong nhóm.
     * <p>
     * Yêu cầu: Người gọi phải là Trưởng nhóm hiện tại, và người nhận quyền phải là thành viên
     * đang hoạt động ({@code ACTIVE}) của nhóm.
     * </p>
     *
     * @param groupId  ID nhóm
     * @param memberId ID thành viên được chỉ định làm Trưởng nhóm mới
     * @throws BusinessException nếu không phải Owner, chuyển cho chính mình,
     *                           hoặc người nhận không phải thành viên hợp lệ
     */
    void transferOwnership(UUID groupId, UUID memberId);

}

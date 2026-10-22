package com.datn.financeapp.module.group.service;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.module.group.dto.request.group.GroupCreateReq;
import com.datn.financeapp.module.group.dto.request.group.GroupJoinReq;
import com.datn.financeapp.module.group.dto.request.group.GroupUpdateReq;
import com.datn.financeapp.module.group.dto.response.group.GroupDetailRes;
import com.datn.financeapp.module.group.dto.response.group.GroupPendingCountRes;
import com.datn.financeapp.module.group.dto.response.group.GroupSummaryRes;

import java.util.List;
import java.util.UUID;

/**
 * Service quản lý thông tin vòng đời của nhóm chung quỹ (Group), cấu hình nhóm,phân quyền thành viên và mã mời tham gia.
 */
public interface GroupService {

    /**
     * Tra cứu chi tiết nhóm chưa bị xóa theo ID (Dành cho Admin hoặc tác vụ nội bộ hệ thống).
     *
     * @param groupId ID nhóm cần tra cứu
     * @return Thông tin chi tiết nhóm (không kèm vai trò cá nhân {@code myRole})
     * @throws BusinessException nếu không tìm thấy nhóm hoặc nhóm đã bị xóa ({@code GROUP_NOT_FOUND})
     */
    GroupDetailRes findNotDeletedById(UUID groupId);

    /**
     * Tạo mới một nhóm chung quỹ.
     * <p>
     * Hệ thống tự động sinh mã mời duy nhất, tạo quỹ khởi tạo và gán người tạo làm Trưởng nhóm ({@code OWNER}).
     * Nếu có danh sách thành viên mời ban đầu, hệ thống sẽ tự động thêm vào nhóm.
     * </p>
     *
     * @param req Dữ liệu tạo nhóm (tên, mô tả, mục tiêu, cài đặt duyệt, danh sách thành viên ban đầu...)
     * @return Chi tiết nhóm sau khi tạo thành công
     */
    GroupDetailRes create(GroupCreateReq req);

    /**
     * lấy toàn bộ group member đang ở
     * <p>Yêu cầu: Người gọi phải là thành viên đang hoạt động ({@code ACTIVE}) của nhóm.</p>
     *
     * @return Danh sách nhóm (thông tin nhóm, vai trò {@code myRole}, quỹ nhóm, danh sách thành viên)
     */
    List<GroupSummaryRes> list();

    /**
     * Xem thông tin chi tiết của một nhóm cụ thể.
     * <p>Yêu cầu: Người gọi phải là thành viên đang hoạt động ({@code ACTIVE}) của nhóm.</p>
     *
     * @param groupId ID nhóm cần xem
     * @return Chi tiết nhóm (thông tin nhóm, vai trò {@code myRole}, quỹ nhóm, danh sách thành viên)
     * @throws BusinessException nếu nhóm không tồn tại ({@code GROUP_NOT_FOUND})
     *                           hoặc người dùng không thuộc nhóm ({@code GROUP_MEMBER_REQUIRED})
     */
    GroupDetailRes detail(UUID groupId);

    /**
     * Cập nhật thông tin cấu hình nhóm (tên, mô tả, mục tiêu chi tiêu, cơ chế duyệt, quyết toán).
     * <p>Yêu cầu quyền: Trưởng nhóm ({@code OWNER}).</p>
     *
     * @param groupId ID nhóm cần cập nhật
     * @param req     Dữ liệu cập nhật
     * @return Chi tiết nhóm sau khi cập nhật
     * @throws BusinessException nếu không phải Owner ({@code GROUP_OWNER_REQUIRED})
     *                           hoặc không tìm thấy nhóm ({@code GROUP_NOT_FOUND})
     */
    GroupDetailRes update(UUID groupId, GroupUpdateReq req);

    /**
     * Lưu trữ nhóm (chuyển trạng thái sang {@code ARCHIVED}).
     * <p>
     * Nhóm lưu trữ chỉ cho phép đọc, không cho phép tạo giao dịch hay thay đổi số dư.
     * Yêu cầu quyền: Trưởng nhóm ({@code OWNER}) và không còn giao dịch chờ duyệt.
     * </p>
     *
     * @param groupId ID nhóm cần lưu trữ
     * @throws BusinessException nếu không phải Owner ({@code GROUP_OWNER_REQUIRED})
     *                           hoặc còn giao dịch chờ duyệt ({@code GROUP_PENDING_TXN_EXIST})
     */
    void archive(UUID groupId);

    /**
     * Mở lại nhóm đã lưu trữ (chuyển trạng thái về {@code ACTIVE}).
     * <p>Yêu cầu quyền: Trưởng nhóm ({@code OWNER}).</p>
     *
     * @param groupId ID nhóm cần mở lại
     * @throws BusinessException nếu không phải Owner ({@code GROUP_OWNER_REQUIRED})
     */
    void unarchive(UUID groupId);

    /**
     * Xóa nhóm (chuyển trạng thái sang {@code DELETED}) và đóng quỹ nhóm ({@code CLOSED}).
     * <p>
     * Yêu cầu quyền: Trưởng nhóm ({@code OWNER}).
     * Điều kiện: Số dư quỹ phải bằng 0 và không còn giao dịch đang chờ duyệt.
     * </p>
     *
     * @param groupId ID nhóm cần xóa
     * @throws BusinessException nếu không phải Owner, quỹ còn số dư ({@code GROUP_DELETE_BALANCE_NOT_ZERO}),
     *                           hoặc còn giao dịch chờ duyệt ({@code GROUP_PENDING_TXN_EXIST})
     */
    void delete(UUID groupId);

    /**
     * Tham gia vào nhóm bằng mã mời.
     * <p>
     * Nếu nhóm bật tự động duyệt ({@code isJoinWithoutConfirm = true}), thành viên vào thẳng trạng thái {@code ACTIVE}.
     * Ngược lại, thành viên vào trạng thái chờ duyệt {@code PENDING}.
     * </p>
     *
     * @throws BusinessException nếu mã mời không hợp lệ ({@code INVITE_CODE_INVALID}),
     *                           nhóm đã lưu trữ ({@code GROUP_ARCHIVED}),
     *                           hoặc đã là thành viên trong nhóm ({@code GROUP_MEMBER_ALREADY_EXISTS})
     */
    void joinByCode(String code);

}

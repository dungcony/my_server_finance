package com.datn.financeapp.group.service;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.group.dto.request.group.GroupCreateReq;
import com.datn.financeapp.group.dto.request.group.GroupJoinReq;
import com.datn.financeapp.group.dto.request.group.GroupUpdateReq;
import com.datn.financeapp.group.dto.response.group.GroupDetailRes;
import com.datn.financeapp.group.dto.response.group.GroupPendingCountRes;
import com.datn.financeapp.group.dto.response.group.GroupSummaryRes;

import java.util.List;
import java.util.UUID;

/**
 * Service quản lý thông tin vòng đời của nhóm chung quỹ (Group), cấu hình nhóm,
 * phân quyền thành viên và mã mời tham gia.
 * <p>
 * Các hàm trong interface:
 * <ul>
 *   <li>{@link #findNotDeletedById}: Tra cứu nhóm nội bộ. Input: groupId. Output: GroupDetailRes.</li>
 *   <li>{@link #create}: Tạo nhóm. Input: operatorId, req. Output: GroupDetailRes.</li>
 *   <li>{@link #list}: Xem ds nhóm của user. Input: operatorId. Output: ds GroupSummaryRes.</li>
 *   <li>{@link #detail(UUID, UUID)}: Xem chi tiết nhóm. Input: operatorId, groupId. Output: GroupDetailRes.</li>
 *   <li>{@link #joinByCode(UUID, GroupJoinReq)}: xin vào nhóm.</li>
 *   <li>{@link #update}: Cập nhật cấu hình. Input: operatorId, groupId, req. Output: GroupDetailRes.</li>
 *   <li>{@link #archive}, {@link #unarchive}: Lưu trữ/Mở lại nhóm. Input: operatorId, groupId. Output: void.</li>
 *   <li>{@link #delete}: Xóa nhóm. Input: operatorId, groupId. Output: void.</li>
 * </ul>
 * </p>
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
     * @param operatorId ID người dùng tạo nhóm (sẽ là OWNER)
     * @param req        Dữ liệu tạo nhóm (tên, mô tả, mục tiêu, cài đặt duyệt, danh sách thành viên ban đầu...)
     * @return Chi tiết nhóm sau khi tạo thành công
     */
    GroupDetailRes create(UUID operatorId, GroupCreateReq req);

    /**
     * lấy toàn bộ group member đang ở
     * <p>Yêu cầu: Người gọi phải là thành viên đang hoạt động ({@code ACTIVE}) của nhóm.</p>
     *
     * @param operatorId ID người dùng yêu cầu
     * @return Danh sách nhóm (thông tin nhóm, vai trò {@code myRole}, quỹ nhóm, danh sách thành viên)
     */
    List<GroupSummaryRes> list(UUID operatorId);

    /**
     * Xem thông tin chi tiết của một nhóm cụ thể.
     * <p>Yêu cầu: Người gọi phải là thành viên đang hoạt động ({@code ACTIVE}) của nhóm.</p>
     *
     * @param operatorId ID người dùng yêu cầu
     * @param groupId    ID nhóm cần xem
     * @return Chi tiết nhóm (thông tin nhóm, vai trò {@code myRole}, quỹ nhóm, danh sách thành viên)
     * @throws BusinessException nếu nhóm không tồn tại ({@code GROUP_NOT_FOUND})
     *                           hoặc người dùng không thuộc nhóm ({@code FORBIDDEN_NOT_GROUP_MEMBER})
     */
    GroupDetailRes detail(UUID operatorId, UUID groupId);

    /**
     * Cập nhật thông tin cấu hình nhóm (tên, mô tả, mục tiêu chi tiêu, cơ chế duyệt, quyết toán).
     * <p>Yêu cầu quyền: Trưởng nhóm ({@code OWNER}).</p>
     *
     * @param operatorId ID người thực hiện (phải là Owner)
     * @param groupId    ID nhóm cần cập nhật
     * @param req        Dữ liệu cập nhật
     * @return Chi tiết nhóm sau khi cập nhật
     * @throws BusinessException nếu không phải Owner ({@code FORBIDDEN_OWNER_REQUIRED})
     *                           hoặc không tìm thấy nhóm ({@code GROUP_NOT_FOUND})
     */
    GroupDetailRes update(UUID operatorId, UUID groupId, GroupUpdateReq req);

    /**
     * Lưu trữ nhóm (chuyển trạng thái sang {@code ARCHIVED}).
     * <p>
     * Nhóm lưu trữ chỉ cho phép đọc, không cho phép tạo giao dịch hay thay đổi số dư.
     * Yêu cầu quyền: Trưởng nhóm ({@code OWNER}) và không còn giao dịch chờ duyệt.
     * </p>
     *
     * @param operatorId ID người thực hiện (phải là Owner)
     * @param groupId    ID nhóm cần lưu trữ
     * @throws BusinessException nếu không phải Owner ({@code FORBIDDEN_OWNER_REQUIRED})
     *                           hoặc còn giao dịch chờ duyệt ({@code GROUP_HAS_PENDING_TRANSACTIONS})
     */
    void archive(UUID operatorId, UUID groupId);

    /**
     * Mở lại nhóm đã lưu trữ (chuyển trạng thái về {@code ACTIVE}).
     * <p>Yêu cầu quyền: Trưởng nhóm ({@code OWNER}).</p>
     *
     * @param operatorId ID người thực hiện (phải là Owner)
     * @param groupId    ID nhóm cần mở lại
     * @throws BusinessException nếu không phải Owner ({@code FORBIDDEN_OWNER_REQUIRED})
     */
    void unarchive(UUID operatorId, UUID groupId);

    /**
     * Xóa nhóm (chuyển trạng thái sang {@code DELETED}) và đóng quỹ nhóm ({@code CLOSED}).
     * <p>
     * Yêu cầu quyền: Trưởng nhóm ({@code OWNER}).
     * Điều kiện: Số dư quỹ phải bằng 0 và không còn giao dịch đang chờ duyệt.
     * </p>
     *
     * @param operatorId ID người thực hiện (phải là Owner)
     * @param groupId    ID nhóm cần xóa
     * @throws BusinessException nếu không phải Owner, quỹ còn số dư ({@code CANNOT_DELETE_GROUP_WITH_BALANCE}),
     *                           hoặc còn giao dịch chờ duyệt ({@code GROUP_HAS_PENDING_TRANSACTIONS})
     */
    void delete(UUID operatorId, UUID groupId);

    /**
     * Tham gia vào nhóm bằng mã mời.
     * <p>
     * Nếu nhóm bật tự động duyệt ({@code isJoinWithoutConfirm = true}), thành viên vào thẳng trạng thái {@code ACTIVE}.
     * Ngược lại, thành viên vào trạng thái chờ duyệt {@code PENDING}.
     * </p>
     *
     * @param operatorId ID người dùng muốn tham gia
     * @param req        DTO chứa mã mời
     * @throws BusinessException nếu mã mời không hợp lệ ({@code INVITE_CODE_INVALID}),
     *                           nhóm đã lưu trữ ({@code GROUP_ARCHIVED}),
     *                           hoặc đã là thành viên trong nhóm ({@code ALREADY_IN_GROUP})
     */
    void joinByCode(UUID operatorId, GroupJoinReq req);

    /**
     * Đếm số việc đang chờ duyệt để hiện badge.
     * <p>
     * Giao dịch {@code PENDING} chỉ trả cho Trưởng nhóm / Thủ quỹ, thành viên {@code PENDING} chỉ trả cho
     * Trưởng nhóm; người không có quyền duyệt nhận 0 ở mục tương ứng.
     * </p>
     *
     * @param operatorId ID người dùng yêu cầu (phải là thành viên {@code ACTIVE})
     * @param groupId    ID nhóm
     * @return số giao dịch chờ duyệt và số thành viên chờ duyệt
     */
    GroupPendingCountRes pendingCount(UUID operatorId, UUID groupId);
}

package com.datn.financeapp.group.helper;

import com.datn.financeapp.group.enums.GroupStatus;
import com.datn.financeapp.group.enums.MemberRole;
import com.datn.financeapp.group.enums.MemberStatus;

import java.util.UUID;

/**
 * Thông tin định danh, vai trò và trạng thái phân quyền của thành viên trong nhóm.
 * <p>
 * Được sử dụng bởi các tầng Service/Validator để kiểm tra quyền hạn thao tác tài chính,
 * cấu hình nhóm hoặc xét duyệt giao dịch.
 * </p>
 * <p>
 * Các hàm trong class:
 * <ul>
 *   <li>{@link #isOwner()}: Kiểm tra quyền trưởng nhóm. Input: không. Output: boolean.</li>
 *   <li>{@link #isTreasurer()}: Kiểm tra quyền thủ quỹ. Input: không. Output: boolean.</li>
 * </ul>
 * </p>
 *
 * @param groupId             ID của nhóm tài chính
 * @param myId                ID của người dùng hiện tại đang xét quyền
 * @param groupStatus         Trạng thái hoạt động của nhóm ({@link GroupStatus})
 * @param isSettlementEnabled Trạng thái nhóm đã bật tính năng quyết toán hay chưa
 * @param memberStatus        Trạng thái thành viên trong nhóm ({@link MemberStatus})
 * @param memberRole          Vai trò của thành viên trong nhóm ({@link MemberRole})
 * @param keepperId            ID của thành viên đang chịu trách nhiệm giữ quỹ nhóm (Thủ quỹ), có thể null
 */
public record MemberAuthInfo(
        UUID groupId,
        UUID myId,
        GroupStatus groupStatus,
        Boolean isSettlementEnabled,
        MemberStatus memberStatus,
        MemberRole memberRole,
        UUID keepperId
) {
    /**
     * Constructor thuận tiện khi nhóm chưa xác định hoặc không chỉ định người giữ quỹ.
     *
     * @param groupId             ID của nhóm tài chính
     * @param myId                ID của người dùng hiện tại
     * @param groupStatus         Trạng thái hoạt động của nhóm
     * @param isSettlementEnabled Nhóm có bật quyết toán không
     * @param memberStatus        Trạng thái thành viên
     * @param memberRole          Vai trò thành viên
     */
    public MemberAuthInfo(
            UUID groupId,
            UUID myId,
            GroupStatus groupStatus,
            Boolean isSettlementEnabled,
            MemberStatus memberStatus,
            MemberRole memberRole
    ) {
        this(groupId, myId, groupStatus, isSettlementEnabled, memberStatus, memberRole, null);
    }

    /**
     * Kiểm tra xem thành viên có vai trò Trưởng nhóm (OWNER) hay không.
     *
     * @return {@code true} nếu vai trò là {@link MemberRole#OWNER}, ngược lại {@code false}
     */
    public boolean isOwner() {
        return memberRole == MemberRole.OWNER;
    }

    /**
     * Kiểm tra xem thành viên hiện tại có đang nắm giữ quỹ nhóm (Thủ quỹ) hay không.
     *
     * @return {@code true} nếu {@code myId} khớp với {@code keepperId}, ngược lại {@code false}
     */
    public boolean isTreasurer() {
        return keepperId != null && keepperId.equals(myId);
    }
}

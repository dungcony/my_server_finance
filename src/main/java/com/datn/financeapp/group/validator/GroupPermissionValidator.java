package com.datn.financeapp.group.validator;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.entity.Group;
import com.datn.financeapp.group.enums.GroupRole;
import com.datn.financeapp.group.enums.GroupStatus;
import com.datn.financeapp.group.enums.MemberStatus;
import com.datn.financeapp.group.repository.GroupMemberRepository;
import com.datn.financeapp.group.repository.GroupRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Validator kiểm tra tư cách thành viên, phân quyền thao tác và tính hợp lệ của nhóm tài chính.
 * <p>
 * Tái sử dụng tập trung cho các service trong module {@code group}, tránh duplicate code
 * và nhất quán trong việc bắn các lỗi {@link BusinessException} liên quan đến quyền truy cập.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class GroupPermissionValidator {

    private final GroupMemberRepository groupMemberRepository;
    private final GroupRepository groupRepository;

    /**
     * Xác thực người dùng phải là thành viên đang hoạt động (ACTIVE) trong nhóm.
     *
     * @param groupId ID nhóm
     * @param userId  ID người dùng
     * @throws BusinessException nếu không phải thành viên ACTIVE ({@link ErrorCode#FORBIDDEN_NOT_GROUP_MEMBER})
     */
    public void verifyActiveMember(UUID groupId, UUID userId) {
        boolean isMember = groupMemberRepository.existsByGroupIdAndUserIdAndStatus(
                groupId, userId, MemberStatus.ACTIVE
        );
        if (!isMember) {
            throw new BusinessException(ErrorCode.FORBIDDEN_NOT_GROUP_MEMBER);
        }
    }

    /**
     * Xác thực người dùng phải là Trưởng nhóm (OWNER) đang hoạt động.
     *
     * @param groupId ID nhóm
     * @param userId  ID người dùng
     * @throws BusinessException nếu không phải Owner ({@link ErrorCode#FORBIDDEN_OWNER_REQUIRED})
     */
    public void verifyOwnerRole(UUID groupId, UUID userId) {
        if (!isOwner(groupId, userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_OWNER_REQUIRED);
        }
    }

    /**
     * Kiểm tra người dùng có phải là Trưởng nhóm (OWNER) đang hoạt động hay không.
     *
     * @param groupId ID nhóm
     * @param userId  ID người dùng
     * @return {@code true} nếu là Owner ACTIVE, ngược lại {@code false}
     */
    public boolean isOwner(UUID groupId, UUID userId) {
        return groupMemberRepository.existsByGroupIdAndUserIdAndRoleAndStatus(
                groupId, userId, GroupRole.OWNER, MemberStatus.ACTIVE
        );
    }

    /**
     * Xác thực người dùng phải là Trưởng nhóm (OWNER) hoặc Thủ quỹ (người đang giữ ví).
     *
     * @param groupId      ID nhóm
     * @param userId       ID người dùng đang thực hiện thao tác
     * @param heldByUserId ID người dùng đang giữ ví
     * @throws BusinessException nếu không phải Owner và không phải Thủ quỹ ({@link ErrorCode#FORBIDDEN_TREASURER_REQUIRED})
     */
    public void verifyOwnerOrTreasurer(UUID groupId, UUID userId, UUID heldByUserId) {
        boolean isTreasurer = heldByUserId != null && heldByUserId.equals(userId);
        boolean isOwner = isOwner(groupId, userId);
        if (!isOwner && !isTreasurer) {
            throw new BusinessException(ErrorCode.FORBIDDEN_TREASURER_REQUIRED);
        }
    }

    /**
     * Tìm và xác thực nhóm tồn tại, chưa bị xóa (DELETED).
     *
     * @param groupId ID nhóm
     * @return {@link Group} nếu hợp lệ
     * @throws BusinessException nếu nhóm không tồn tại hoặc đã bị xóa ({@link ErrorCode#GROUP_NOT_FOUND})
     */
    public Group validateAndGetGroup(UUID groupId) {
        return groupRepository.findById(groupId)
                .filter(g -> g.getStatus() != GroupStatus.DELETED)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
    }

    /**
     * Tìm và xác thực nhóm tồn tại, chưa bị xóa và chưa bị lưu trữ (ARCHIVED).
     *
     * @param groupId ID nhóm
     * @return {@link Group} đang hoạt động
     * @throws BusinessException nếu nhóm không tồn tại/đã xóa ({@link ErrorCode#GROUP_NOT_FOUND})
     *                           hoặc đã bị lưu trữ ({@link ErrorCode#GROUP_ARCHIVED})
     */
    public Group validateAndGetActiveGroup(UUID groupId) {
        Group group = validateAndGetGroup(groupId);
        if (group.getStatus() == GroupStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.GROUP_ARCHIVED);
        }
        return group;
    }
}

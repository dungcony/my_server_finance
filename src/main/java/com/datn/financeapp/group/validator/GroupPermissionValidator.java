package com.datn.financeapp.group.validator;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.entity.GTransaction;
import com.datn.financeapp.group.enums.*;
import com.datn.financeapp.group.helper.MemberAuthInfo;
import com.datn.financeapp.group.repository.GroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Validator kiểm tra tư cách thành viên, phân quyền thao tác và tính hợp lệ của
 * nhóm tài chính.
 * <p>
 * Tái sử dụng tập trung cho các service trong module {@code group}, tránh
 * duplicate code
 * và nhất quán trong việc bắn các lỗi {@link BusinessException} liên quan đến
 * quyền truy cập.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class GroupPermissionValidator {

    private final GroupRepository groupRepository;

    /**
     * Lấy thông tin auth và xác thực trạng thái nhóm (ACTIVE) và tư cách thành viên (ACTIVE).
     * Mặc định chặn nhóm đã lưu trữ — dùng cho mọi thao tác ghi.
     *
     * @param groupId    ID nhóm
     * @param operatorId ID người dùng
     * @return {@link MemberAuthInfo} chứa trạng thái và role
     */
    public MemberAuthInfo getAuthInfo(UUID groupId, UUID operatorId) {
        return getAuthInfo(groupId, operatorId, false);
    }

    /**
     * Lấy thông tin auth và xác thực nhóm chưa bị xoá cùng tư cách thành viên (ACTIVE).
     *
     * @param groupId       ID nhóm
     * @param operatorId    ID người dùng
     * @param allowArchived {@code true} cho thao tác chỉ đọc (và xoá/mở lại nhóm), {@code false} thì nhóm đã lưu trữ
     *                      bị chặn bằng {@link ErrorCode#GROUP_ARCHIVED}
     * @return {@link MemberAuthInfo} chứa trạng thái và role
     */
    public MemberAuthInfo getAuthInfo(UUID groupId, UUID operatorId, boolean allowArchived) {
        if (groupId == null || operatorId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        return validateAuthInfo(
                groupRepository.findAuthInfo(groupId, operatorId),
                allowArchived
        );
    }

    /**
     * Lấy thông tin auth và xác thực trạng thái nhóm (ACTIVE) và tư cách thành viên (ACTIVE)
     *
     * @param inviteCode mã mời nhóm
     * @param operatorId ID người dùng
     * @return {@link MemberAuthInfo} chứa trạng thái và role
     */
    public MemberAuthInfo getAuthInfo(String inviteCode, UUID operatorId) {
        if (inviteCode == null || operatorId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }


        return validateAuthInfo(
                groupRepository.findAuthInfo(inviteCode, operatorId),
                false
        );
    }

    /**
     * Xác thực người dùng phải là thành viên đang hoạt động (ACTIVE) của nhóm chưa bị xoá, không cần vai trò cụ thể.
     * Dành cho chỗ chỉ cần kiểm tra quyền và không dùng thông tin auth trả về.
     *
     * @param groupId       ID nhóm
     * @param operatorId    ID người dùng thực hiện thao tác
     * @param allowArchived {@code true} cho thao tác chỉ đọc: nhóm đã lưu trữ vẫn qua
     * @throws BusinessException nếu không phải thành viên đang hoạt động hoặc nhóm không hợp lệ
     */
    public void verifyMember(UUID groupId, UUID operatorId, boolean allowArchived) {
        getAuthInfo(groupId, operatorId, allowArchived);
    }

    /**
     * Xác thực người dùng phải là Trưởng nhóm (OWNER) đang hoạt động.
     * Mặc định chặn nhóm đã lưu trữ — dùng cho mọi thao tác ghi.
     *
     * @param groupId    ID nhóm
     * @param operatorId ID người dùng thực hiện thao tác
     * @throws BusinessException nếu không phải Owner
     *                           ({@link ErrorCode#GROUP_OWNER_REQUIRED})
     */
    public void verifyOwner(UUID groupId, UUID operatorId) {
        verifyOwner(groupId, operatorId, false);
    }

    /**
     * Xác thực người dùng phải là Trưởng nhóm (OWNER) đang hoạt động.
     *
     * @param groupId       ID nhóm
     * @param operatorId    ID người dùng thực hiện thao tác
     * @param allowArchived {@code true} cho thao tác vẫn làm được khi nhóm đã lưu trữ (xoá, mở lại nhóm)
     * @throws BusinessException nếu không phải Owner
     *                           ({@link ErrorCode#GROUP_OWNER_REQUIRED})
     */
    public void verifyOwner(UUID groupId, UUID operatorId, boolean allowArchived) {

        var info = getAuthInfo(groupId, operatorId, allowArchived);

        if (info.memberRole() != MemberRole.OWNER)
            throw new BusinessException(ErrorCode.GROUP_OWNER_REQUIRED);
    }

    /**
     * Xác thực người dùng phải là Trưởng nhóm (OWNER) hoặc Thủ quỹ (người đang giữ quỹ).
     *
     * @param groupId    ID nhóm
     * @param operatorId ID người dùng đang thực hiện thao tác
     * @param keepperId  ID người dùng đang giữ quỹ
     * @throws BusinessException nếu không phải Owner và không phải Thủ quỹ
     *                           ({@link ErrorCode#GROUP_TREASURER_REQUIRED})
     */
    public void verifyOwnerOrTreasurer(UUID groupId, UUID operatorId, UUID keepperId) {
        var info = getAuthInfo(groupId, operatorId);
        boolean isOwner = info.memberRole() == MemberRole.OWNER;
        boolean isTreasurer = keepperId != null && keepperId.equals(operatorId);
        if (!isOwner && !isTreasurer)
            throw new BusinessException(ErrorCode.GROUP_TREASURER_REQUIRED);
    }

    /**
     * Xác thực quyền chỉnh sửa giao dịch tài chính nhóm.
     * <p>
     * Quy tắc:
     * <ul>
     * <li>Trưởng nhóm (OWNER) và Thủ quỹ (TREASURER): Được sửa mọi giao dịch.</li>
     * <li>Thành viên thường: Chỉ được sửa giao dịch do chính mình tạo (EXPENSE,
     * CONTRIBUTION).</li>
     * </ul>
     * </p>
     *
     * @param txn        Giao dịch cần chỉnh sửa
     * @param operatorId ID người thực hiện chỉnh sửa
     * @param authInfo   Thông tin quyền hạn của người thực hiện trong nhóm
     */
    public void verifyTransactionEditPermission(GTransaction txn, UUID operatorId, MemberAuthInfo authInfo) {
        // trưởng nhóm và thủ quỹ có toàn quyền sửa mọi giao dịch
        if (authInfo.isOwner() || authInfo.isTreasurer()) {
            return;
        }

        // thành viên thường chỉ được sửa giao dịch do chính mình tạo
        boolean isCreator = txn.getCreatedBy().equals(operatorId);
        if (!isCreator) {
            throw new BusinessException(ErrorCode.GROUP_TXN_EDIT_FORBIDDEN);
        }

        // thành viên thường không được sửa giao dịch đã duyệt
        if (txn.getStatus() == GTransactionStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.GROUP_TXN_CONFIRMED_EDIT_FORBIDDEN);
        }

        // thành viên thường không được sửa giao dịch can thiệp trực tiếp vào quỹ
        if (txn.getType() != GTransactionType.EXPENSE && txn.getType() != GTransactionType.CONTRIBUTION) {
            throw new BusinessException(ErrorCode.GROUP_TREASURER_REQUIRED);
        }
    }

    /**
     * Xác thực quyền xóa giao dịch tài chính nhóm.
     * <p>
     * Quy tắc:
     * <ul>
     * <li>Trưởng nhóm (OWNER): Xóa được mọi giao dịch.</li>
     * <li>Thủ quỹ (TREASURER): Không có quyền xóa.</li>
     * <li>Thành viên thường: Chỉ xóa giao dịch do chính mình tạo và đang PENDING.</li>
     * </ul>
     *
     * @param txn        Giao dịch cần xóa
     * @param operatorId ID người thực hiện xóa
     * @param authInfo   Thông tin quyền hạn của người thực hiện trong nhóm
     */
    public void verifyTransactionDeletePermission(GTransaction txn, UUID operatorId, MemberAuthInfo authInfo) {
        // chủ nhóm xóa được mọi giao dịch
        if (authInfo.isOwner()) return;

        // thủ quỹ không có quyền xóa
        if (authInfo.isTreasurer())
            throw new BusinessException(ErrorCode.GROUP_TXN_DELETE_FORBIDDEN);

        // thành viên thường chỉ xóa giao dịch do mình tạo
        if (!txn.getCreatedBy().equals(operatorId))
            throw new BusinessException(ErrorCode.GROUP_TXN_DELETE_FORBIDDEN);

        // và chỉ khi giao dịch còn chờ duyệt
        if (txn.getStatus() != GTransactionStatus.PENDING)
            throw new BusinessException(ErrorCode.GROUP_TXN_DELETE_FORBIDDEN);
    }

    private MemberAuthInfo validateAuthInfo(Optional<MemberAuthInfo> authInfo, boolean allowArchived) {
        MemberAuthInfo info = requireNotDeleted(authInfo);

        if (!allowArchived && info.groupStatus() == GroupStatus.ARCHIVED)
            throw new BusinessException(ErrorCode.GROUP_ARCHIVED);

        return info;
    }

    private MemberAuthInfo requireNotDeleted(Optional<MemberAuthInfo> authInfo) {
        return authInfo
                .filter(i -> i.groupStatus() != GroupStatus.DELETED)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
    }
}

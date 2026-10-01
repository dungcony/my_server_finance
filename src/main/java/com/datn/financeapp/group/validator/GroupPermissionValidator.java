package com.datn.financeapp.group.validator;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.entity.GTransaction;
import com.datn.financeapp.group.enums.GroupStatus;
import com.datn.financeapp.group.enums.MemberRole;
import com.datn.financeapp.group.enums.MemberStatus;
import com.datn.financeapp.group.enums.TransactionType;
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
     * Lấy thông tin auth và xác thực trạng thái nhóm (ACTIVE) và tư cách thành viên (ACTIVE)
     *
     * @param groupId    ID nhóm
     * @param operatorId ID người dùng
     * @return {@link MemberAuthInfo} chứa trạng thái và role
     */
    public MemberAuthInfo getAuthInfo(UUID groupId, UUID operatorId) {
        if (groupId == null || operatorId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        return validateAuthInfo(
                groupRepository.findAuthInfo(groupId, operatorId)
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
                groupRepository.findAuthInfo(inviteCode, operatorId)
        );
    }

    /**
     * Xác thực người dùng phải là Trưởng nhóm (OWNER) đang hoạt động.
     *
     * @param groupId    ID nhóm
     * @param operatorId ID người dùng thực hiện thao tác
     * @throws BusinessException nếu không phải Owner
     *                           ({@link ErrorCode#FORBIDDEN_OWNER_REQUIRED})
     */
    public void verifyOwner(UUID groupId, UUID operatorId) {

        var info = getAuthInfo(groupId, operatorId);

        if (info.memberRole() != MemberRole.OWNER)
            throw new BusinessException(ErrorCode.FORBIDDEN_OWNER_REQUIRED);

    }

    public void verifyOwnerAllowArchived(UUID groupId, UUID operatorId) {
        if (groupId == null || operatorId == null)
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);

        MemberAuthInfo info = requireNotDeleted(groupRepository.findAuthInfo(groupId, operatorId));

        if (info.memberRole() != MemberRole.OWNER)
            throw new BusinessException(ErrorCode.FORBIDDEN_OWNER_REQUIRED);
    }

    /**
     * Xác thực người dùng phải là Trưởng nhóm (OWNER) hoặc Thủ quỹ (người đang giữ quỹ).
     *
     * @param groupId    ID nhóm
     * @param operatorId ID người dùng đang thực hiện thao tác
     * @param keepperId  ID người dùng đang giữ quỹ
     * @throws BusinessException nếu không phải Owner và không phải Thủ quỹ
     *                           ({@link ErrorCode#FORBIDDEN_TREASURER_REQUIRED})
     */
    public void verifyOwnerOrTreasurer(UUID groupId, UUID operatorId, UUID keepperId) {
        var info = getAuthInfo(groupId, operatorId);
        boolean isOwner = info.memberRole() == MemberRole.OWNER;
        boolean isTreasurer = keepperId != null && keepperId.equals(operatorId);
        if (!isOwner && !isTreasurer)
            throw new BusinessException(ErrorCode.FORBIDDEN_TREASURER_REQUIRED);
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
            throw new BusinessException(ErrorCode.FORBIDDEN_TRANSACTION_EDIT);
        }

        // thành viên thường không được sửa giao dịch can thiệp trực tiếp vào quỹ
        if (txn.getType() != TransactionType.EXPENSE && txn.getType() != TransactionType.CONTRIBUTION) {
            throw new BusinessException(ErrorCode.FORBIDDEN_TREASURER_REQUIRED);
        }
    }

    private MemberAuthInfo validateAuthInfo(Optional<MemberAuthInfo> authInfo) {
        MemberAuthInfo info = requireNotDeleted(authInfo);

        if (info.groupStatus() == GroupStatus.ARCHIVED)
            throw new BusinessException(ErrorCode.GROUP_ARCHIVED);

        return info;
    }

    private MemberAuthInfo requireNotDeleted(Optional<MemberAuthInfo> authInfo) {
        return authInfo
                .filter(i -> i.groupStatus() != GroupStatus.DELETED)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
    }
}

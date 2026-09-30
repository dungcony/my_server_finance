package com.datn.financeapp.group.validator;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.enums.MemberRole;
import com.datn.financeapp.group.enums.GroupStatus;
import com.datn.financeapp.group.enums.MemberStatus;
import com.datn.financeapp.group.helper.MemberAuthInfo;
import com.datn.financeapp.group.repository.MemberRepository;
import com.datn.financeapp.group.repository.GroupRepository;

import java.util.UUID;

import com.datn.financeapp.group.entity.GTransaction;
import com.datn.financeapp.group.enums.TransactionType;
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

    private final MemberRepository memberRepository;
    private final GroupRepository groupRepository;


    /**
     * Xác thực cả trạng thái nhóm (ACTIVE) và tư cách thành viên (ACTIVE) chỉ bằng 1 DB roundtrip duy nhất.
     *
     * @param groupId    ID nhóm
     * @param operatorId ID người dùng
     * @return {@link MemberAuthInfo} chứa trạng thái và role để tái sử dụng mà không cần query lại
     */
    public MemberAuthInfo verifyActiveMemberInGroupActive(UUID groupId, UUID operatorId) {
        MemberAuthInfo info = groupRepository.findAuthInfo(groupId, operatorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

        // Kiểm tra trạng thái Nhóm
        if (info.groupStatus() == GroupStatus.DELETED) {
            throw new BusinessException(ErrorCode.GROUP_NOT_FOUND);
        }
        if (info.groupStatus() == GroupStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.GROUP_ARCHIVED);
        }

        // Kiểm tra trạng thái Thành viên
        if (info.memberStatus() != MemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.FORBIDDEN_NOT_GROUP_MEMBER);
        }

        return info;
    }

    /**
     * Xác thực người dùng phải là Trưởng nhóm (OWNER) đang hoạt động.
     *
     * @param groupId    ID nhóm
     * @param operatorId ID người dùng thực hiện thao tác
     * @throws BusinessException nếu không phải Owner ({@link ErrorCode#FORBIDDEN_OWNER_REQUIRED})
     */
    public MemberAuthInfo verifyOwnerInGroupActive(UUID groupId, UUID operatorId) {

        var info = verifyActiveMemberInGroupActive(groupId, operatorId);

        if (info.memberRole() != MemberRole.OWNER) {
            throw new BusinessException(ErrorCode.FORBIDDEN_OWNER_REQUIRED);
        }
        return info;
    }

    /**
     * Xác thực người dùng phải là Trưởng nhóm (OWNER) hoặc Thủ quỹ (người đang giữ quỹ).
     *
     * @param groupId      ID nhóm
     * @param operatorId   ID người dùng đang thực hiện thao tác
     * @param heldByUserId ID người dùng đang giữ quỹ
     * @throws BusinessException nếu không phải Owner và không phải Thủ quỹ ({@link ErrorCode#FORBIDDEN_TREASURER_REQUIRED})
     */
    public void verifyOwnerOrTreasurer(UUID groupId, UUID operatorId, UUID heldByUserId) {
        var info = verifyActiveMemberInGroupActive(groupId, operatorId);
        boolean isOwner = info.memberRole() == MemberRole.OWNER;
        boolean isTreasurer = heldByUserId != null && heldByUserId.equals(operatorId);
        if (!isOwner && !isTreasurer) {
            throw new BusinessException(ErrorCode.FORBIDDEN_TREASURER_REQUIRED);
        }
    }

    /**
     * Xác thực quyền chỉnh sửa giao dịch tài chính nhóm.
     * <p>
     * Quy tắc:
     * <ul>
     *   <li>Trưởng nhóm (OWNER) và Thủ quỹ (TREASURER): Được sửa mọi giao dịch.</li>
     *   <li>Thành viên thường: Chỉ được sửa giao dịch do chính mình tạo (EXPENSE, CONTRIBUTION).</li>
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
}

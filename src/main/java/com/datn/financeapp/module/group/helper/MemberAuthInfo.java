package com.datn.financeapp.module.group.helper;

import com.datn.financeapp.module.group.entity.GroupTxn;
import com.datn.financeapp.module.group.enums.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.UUID;

/**
 * Thông tin định danh, vai trò và trạng thái phân quyền của thành viên trong nhóm.
 */
public record MemberAuthInfo(
        UUID groupId,
        UUID myId,
        GroupStatus groupStatus,
        Boolean isSettlementEnabled,
        MemberStatus myStatus,
        MemberRole myRole,
        UUID fundkeeperId
) {

    /**
     * Kiểm tra xem thành viên có vai trò Trưởng nhóm (OWNER) hay không.
     *
     * @return {@code true} nếu vai trò là {@link MemberRole#OWNER}, ngược lại {@code false}
     */
    @JsonIgnore
    public boolean isOwner() {
        return myRole == MemberRole.OWNER;
    }

    /**
     * Kiểm tra xem thành viên hiện tại có đang nắm giữ quỹ nhóm (Thủ quỹ) hay không.
     *
     * @return {@code true} nếu {@code myId} khớp với {@code keepperId}, ngược lại {@code false}
     */
    @JsonIgnore
    public boolean isTreasurer() {
        return fundkeeperId != null && fundkeeperId.equals(myId);
    }

    /**
     * Kiểm tra xem thành viên hiện tại có active không.
     *
     * @return {@code true} nếu {@code myId} khớp với {@code keepperId}, ngược lại {@code false}
     */
    @JsonIgnore
    public boolean isMember() {
        return myRole == MemberRole.MEMBER && (fundkeeperId == null || !fundkeeperId.equals(myId));
    }

    @JsonIgnore
    public boolean isEditGroupTransaction(GroupTxn transaction) {

        if (transaction.getType() == GroupTxnType.ADJUSTMENT_UP || transaction.getType() == GroupTxnType.ADJUSTMENT_DOWN)
            return false;

        if (!transaction.getGroupId().equals(groupId))
            return false;

        // Chỉ thủ quỹ được thay đổi
        if (isTreasurer())
            return true;

        // Nếu k phải thủ quỹ thì phải là do chính member đó tạo
        if (!transaction.getCreatedBy().equals(myId))
            return false;

        // member thường chỉ thay đổi giao dịch đang pending
        return transaction.getStatus() == GroupTxnStatus.PENDING;
    }

    public boolean inGroupNotDeleted() {
        return groupStatus != GroupStatus.DELETED;
    }

    public boolean inGroupActive() {
        return groupStatus == GroupStatus.ACTIVE;
    }

    public boolean isCreatorOrTransactorTxn(GroupTxn transaction) {
        return myId.equals(transaction.getCreatedBy()) || myId.equals(transaction.getTransactorId());
    }
}

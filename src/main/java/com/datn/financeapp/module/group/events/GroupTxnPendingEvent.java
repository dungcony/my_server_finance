package com.datn.financeapp.module.group.events;

import com.datn.financeapp.module.group.entity.GroupTxn;
import com.datn.financeapp.module.group.enums.GroupTxnType;
import com.datn.financeapp.module.group.enums.MoneySource;

import java.util.UUID;

/**
 * Sự kiện phát ra khi một giao dịch nhóm được tạo ở trạng thái {@code PENDING}. Listener lắng nghe
 * sau khi transaction commit để báo chủ nhóm và thủ quỹ có giao dịch chờ duyệt.
 *
 * <p>Record bất biến chỉ chứa dữ liệu thô, không mang entity JPA.
 *
 * @param fundKeeperId Id thủ quỹ
 * @param groupTxnId   ID giao dịch nhóm
 * @param groupId      ID nhóm
 * @param createdBy    người tạo giao dịch, không được báo lại
 * @param type         loại giao dịch
 */
public record GroupTxnPendingEvent(
        UUID fundKeeperId,
        UUID groupTxnId,
        UUID groupId,
        UUID createdBy,
        GroupTxnType type) {
}

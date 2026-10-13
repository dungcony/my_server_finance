package com.datn.financeapp.module.group.events;

import com.datn.financeapp.module.group.entity.GroupTxn;
import com.datn.financeapp.module.group.entity.GroupTxnParticipant;
import com.datn.financeapp.module.group.enums.GroupTxnType;
import com.datn.financeapp.module.group.enums.MoneySource;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Sự kiện phát ra khi một giao dịch nhóm đã {@code CONFIRMED} (tạo luôn bởi chủ nhóm/thủ quỹ,
 * hoặc được duyệt sau). Listener lắng nghe sau khi transaction commit để hỏi những người liên quan
 * có muốn ghi khoản này vào sổ cá nhân không.
 *
 * <p>Record bất biến chỉ chứa dữ liệu thô, không mang entity JPA: listener chạy ở thread khác, sau
 * khi transaction đóng nên không đọc được danh sách {@code participants} lazy của entity.
 *
 * @param groupTxnId        ID giao dịch nhóm
 * @param groupId           ID nhóm
 * @param type              loại giao dịch
 * @param amount            tổng số tiền giao dịch
 * @param transactorId      người bỏ tiền túi, null khi dùng tiền quỹ
 * @param categoryId        ID danh mục của giao dịch, null với góp quỹ, hoàn tiền, kiểm kê
 * @param occurredAt        thời điểm phát sinh giao dịch
 * @param note              ghi chú, có thể null
 * @param participantShares phần của từng người tham gia (userId → shareAmount); với hoàn tiền là phần mỗi người được nhận
 */
public record GroupTxnConfirmedEvent(
        UUID groupTxnId,
        UUID groupId,
        GroupTxnType type,
        long amount,
        UUID transactorId,
        UUID categoryId,
        Instant occurredAt,
        String note,
        Map<UUID, Long> participantShares) {

    /**
     * Chụp dữ liệu cần thiết từ giao dịch nhóm vừa được xác nhận.
     *
     * @param txn giao dịch nhóm ở trạng thái {@code CONFIRMED}
     * @return event bất biến, không giữ tham chiếu tới entity
     */
    public static GroupTxnConfirmedEvent of(GroupTxn txn) {
        Map<UUID, Long> shares = txn.getParticipants().stream()
                .collect(Collectors.toMap(GroupTxnParticipant::getUserId, GroupTxnParticipant::getShareAmount));

        return new GroupTxnConfirmedEvent(
                txn.getId(),
                txn.getGroupId(),
                txn.getType(),
                txn.getAmount(),
                txn.getTransactorId(),
                txn.getCategoryId(),
                txn.getOccurredAt(),
                txn.getNote(),
                shares);
    }
}

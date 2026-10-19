package com.datn.financeapp.module.notification.events.listener;

import com.datn.financeapp.module.group.enums.GroupTxnType;
import com.datn.financeapp.module.group.events.GroupTxnConfirmedEvent;
import com.datn.financeapp.module.group.events.GroupTxnPendingEvent;
import com.datn.financeapp.module.notification.enums.ActionNoti;
import com.datn.financeapp.module.notification.enums.NotiType;
import com.datn.financeapp.module.notification.service.NotificationService;
import com.datn.financeapp.module.transaction.enums.TxnType;
import com.datn.financeapp.module.user.dto.response.UserNameDisplayRes;
import com.datn.financeapp.module.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.text.NumberFormat;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Gửi thông báo cho giao dịch nhóm, chạy {@code @Async} sau khi transaction của giao dịch commit nên
 * lỗi gửi thông báo không làm hỏng giao dịch nhóm, chỉ được ghi log.
 */
@Slf4j
@Async("notificationTaskExecutor")
@Component
@RequiredArgsConstructor
public class GroupTxnListener {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final NotificationService notificationService;
    private final UserService userService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConfirmed(GroupTxnConfirmedEvent event) {
        try {
            switch (event.type()) {
                case EXPENSE -> {
                    // Chi tiêu bằng tiền quỹ không phải tiền túi cá nhân -> không gửi thông báo ghi sổ
                    if (event.transactorId() == null) {
                        return;
                    }
                    sendRecordNotification(
                            event.transactorId(),
                            event,
                            event.amount(),
                            TxnType.EXPENSE,
                            event.categoryCode()
                    );
                }
                case CONTRIBUTION -> {
                    if (event.transactorId() != null) {
                        String categoryCode = event.categoryCode() != null ? event.categoryCode() : "gop_quy_nhom";
                        sendRecordNotification(
                                event.transactorId(),
                                event,
                                event.amount(),
                                TxnType.EXPENSE,
                                categoryCode
                        );
                    }
                }
                case REFUND -> {
                    // Hoàn tiền: gửi thông báo ghi sổ thu nhập cho từng người được nhận tiền
                    if (event.participantShares() != null) {
                        for (Map.Entry<UUID, Long> entry : event.participantShares().entrySet()) {
                            UUID receiverId = entry.getKey();
                            long shareAmount = entry.getValue();
                            sendRecordNotification(
                                    receiverId,
                                    event,
                                    shareAmount,
                                    TxnType.INCOME,
                                    "nhan_hoan_tien_nhom"
                            );
                        }
                    }
                }
                case ADJUSTMENT_UP, ADJUSTMENT_DOWN -> {
                    // Kiểm kê quỹ -> không gửi thông báo ghi sổ cá nhân
                }
            }
        } catch (RuntimeException ex) {
            log.error("Gửi thông báo ghi sổ cá nhân cho giao dịch nhóm {} thất bại", event.groupTxnId(), ex);
        }
    }

    private void sendRecordNotification(UUID userId, GroupTxnConfirmedEvent event, long amount, TxnType txnType, String categoryCode) {
        Map<String, Object> data = new HashMap<>();
        data.put("action", ActionNoti.RECORD);
        data.put("group_id", event.groupId().toString());
        data.put("txn_type", txnType.name());
        data.put("amount", amount);
        if (categoryCode != null) {
            data.put("category_code", categoryCode);
        }
        data.put("date", event.occurredAt().atZone(VIETNAM_ZONE).toLocalDate().toString());
        if (event.note() != null) {
            data.put("note", event.note());
        }

        notificationService.createNotification(
                userId,
                NotiType.GROUP_TXN_RECORD_REQUEST,
                "Ghi vào sổ cá nhân?",
                "Giao dịch " + describe(event.type()) + " với số tiền " + formatAmount(amount) + "vnđ đã được xác nhận. Bạn có muốn ghi vào sổ cá nhân không?",
                event.groupTxnId(),
                data
        );
        log.info("Gửi thông báo ghi sổ cá nhân thành công cho user {} giao dịch nhóm {}", userId, event.groupTxnId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPending(GroupTxnPendingEvent event) {
        try {
            String creatorName = resolveUserName(event.createdBy());
            String content = "Có giao dịch " + describe(event.type()) + " mới được tạo bởi " + creatorName + " đang chờ bạn duyệt.";
            Map<String, Object> data = Map.of(
                    "action", ActionNoti.NOTIFY,
                    "group_id", event.groupId().toString(),
                    "group_txn_id", event.groupTxnId().toString()
            );

            notificationService.createNotification(
                    event.fundKeeperId(),
                    NotiType.GROUP_TXN_REVIEW_REQUEST,
                    "Giao dịch nhóm chờ duyệt",
                    content,
                    event.groupTxnId(),
                    data);

            log.info("Gửi thông báo chờ duyệt giao dịch nhóm {} thành công cho thủ quỹ {}", event.groupTxnId(), event.fundKeeperId());
        } catch (RuntimeException ex) {
            log.error("Gửi thông báo chờ duyệt cho giao dịch nhóm {} thất bại", event.groupTxnId(), ex);
        }
    }

    private String resolveUserName(UUID userId) {
        if (userId == null) {
            return "Thành viên nhóm";
        }
        try {
            Map<UUID, UserNameDisplayRes> names = userService.getNames(List.of(userId));
            UserNameDisplayRes nameRes = names.get(userId);
            if (nameRes != null && nameRes.fullName() != null && !nameRes.fullName().isBlank()) {
                return nameRes.fullName();
            }
        } catch (Exception e) {
            log.warn("Không lấy được tên người dùng {}", userId, e);
        }
        return "Thành viên nhóm";
    }

    // -------------------------------------PRIVATE----------------------------------//

    private String describe(GroupTxnType type) {
        return switch (type) {
            case CONTRIBUTION -> "nộp quỹ";
            case EXPENSE -> "chi tiêu";
            case REFUND -> "nhận hoàn tiền từ quỹ";
            case ADJUSTMENT_UP, ADJUSTMENT_DOWN -> "điều chỉnh quỹ";
        };
    }

    private String formatAmount(long amount) {
        return NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(amount);
    }
}

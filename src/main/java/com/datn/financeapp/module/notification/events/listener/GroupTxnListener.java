package com.datn.financeapp.module.notification.events.listener;

import com.datn.financeapp.module.group.enums.*;
import com.datn.financeapp.module.group.events.GroupTxnConfirmedEvent;
import com.datn.financeapp.module.group.events.GroupTxnPendingEvent;
import com.datn.financeapp.module.notification.enums.ActionNoti;
import com.datn.financeapp.module.notification.enums.NotiType;
import com.datn.financeapp.module.notification.service.NotificationService;
import com.datn.financeapp.module.transaction.enums.TransactionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.text.NumberFormat;
import java.time.ZoneId;
import java.util.*;

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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConfirmed(GroupTxnConfirmedEvent event) {
        try {
            // danh mục, chiều tiền, ngày và tên khoản giống nhau cho mọi người nhận, chỉ khác số tiền
            Map<String, Object> shared = new HashMap<>();
            shared.put("action", ActionNoti.RECORD);
            shared.put("group_id", event.groupId().toString());
            shared.put("txn_type", txnTye(event.type()).name());
            shared.put("amount", event.amount());
            shared.put("category_id", event.categoryId().toString());
            shared.put("date", event.occurredAt().atZone(VIETNAM_ZONE).toLocalDate().toString());
            shared.put("note", event.note());

            notificationService.createNotification(
                    event.transactorId(),
                    NotiType.GROUP_TXN_RECORD_REQUEST,
                    "Ghi vào sổ cá nhân?",
                    "Giao dịch " + describe(event.type()) + " với số tiền " + formatAmount(event.amount()) + "vnđ đã được xác nhận. Bạn có muốn ghi vào sổ cá nhân không?",
                    event.groupTxnId(), shared);

            log.info("gửi thông báo confim giao dịch thành công");

        } catch (RuntimeException ex) {
            log.error("Gửi thông báo ghi sổ cá nhân cho giao dịch nhóm {} thất bại", event.groupTxnId(), ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPending(GroupTxnPendingEvent event) {
        try {
            String content = "Có giao dịch " + describe(event.type()) + " mới được tạo bởi " + event.createdBy() + " đang chờ bạn duyệt.";
            Map<String, Object> data = Map.of(
                    "action", ActionNoti.NOTIFY,
                    "group_id", event.groupId().toString(),
                    "group_txn_id", event.groupTxnId()
            );

            notificationService.createNotification(
                    event.fundKeeperId(),
                    NotiType.GROUP_TXN_RECORD_REQUEST,
                    "Ghi vào sổ cá nhân?",
                    content,
                    event.groupTxnId(),
                    data);

        } catch (RuntimeException ex) {
            log.error("Gửi thông báo chờ duyệt cho giao dịch nhóm {} thất bại", event.groupTxnId(), ex);
        }
    }

    // -------------------------------------PRIVATE----------------------------------//

    // hoàn tiền là tiền vào ví, các loại còn lại là tiền ra khỏi ví
    private TransactionType txnTye(GroupTxnType type) {
        return type == GroupTxnType.REFUND ? TransactionType.INCOME : TransactionType.EXPENSE;
    }

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

package com.datn.financeapp.unitTest.notification.events.listener;

import com.datn.financeapp.module.notification.events.listener.*;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datn.financeapp.module.group.enums.GroupTxnType;
import com.datn.financeapp.module.group.events.GroupTxnConfirmedEvent;
import com.datn.financeapp.module.group.events.GroupTxnPendingEvent;
import com.datn.financeapp.module.notification.enums.NotiType;
import com.datn.financeapp.module.notification.service.NotificationService;
import com.datn.financeapp.module.user.dto.response.UserNameDisplayRes;
import com.datn.financeapp.module.user.enums.UserStatus;
import com.datn.financeapp.module.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class GroupTxnListenerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserService userService;

    @InjectMocks
    private GroupTxnListener groupTxnListener;

    @Test
    @DisplayName("onConfirmed: Giao dịch chi tiêu từ tiền quỹ (transactorId == null) thì không gửi thông báo ghi sổ cá nhân")
    void onConfirmed_expenseFromFund_noNotificationSent() {
        GroupTxnConfirmedEvent event = new GroupTxnConfirmedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                GroupTxnType.EXPENSE,
                500_000L,
                null, // tiền quỹ nên transactorId null
                "an_uong",
                Instant.now(),
                "Ăn trưa nhóm",
                Map.of(UUID.randomUUID(), 250_000L, UUID.randomUUID(), 250_000L)
        );

        groupTxnListener.onConfirmed(event);

        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("onConfirmed: Giao dịch chi tiêu tiền túi (transactorId != null) gửi thông báo cho transactor")
    void onConfirmed_expenseFromPersonal_notificationSentToTransactor() {
        UUID transactorId = UUID.randomUUID();
        UUID groupTxnId = UUID.randomUUID();
        GroupTxnConfirmedEvent event = new GroupTxnConfirmedEvent(
                groupTxnId,
                UUID.randomUUID(),
                GroupTxnType.EXPENSE,
                300_000L,
                transactorId,
                "an_uong",
                Instant.now(),
                "Mua nước",
                Map.of()
        );

        groupTxnListener.onConfirmed(event);

        verify(notificationService).createNotification(
                eq(transactorId),
                eq(NotiType.GROUP_TXN_RECORD_REQUEST),
                eq("Ghi vào sổ cá nhân?"),
                any(),
                eq(groupTxnId),
                any()
        );
    }

    @Test
    @DisplayName("onConfirmed: Giao dịch nộp quỹ (CONTRIBUTION) gửi thông báo cho người nộp")
    void onConfirmed_contribution_notificationSentToTransactor() {
        UUID transactorId = UUID.randomUUID();
        UUID groupTxnId = UUID.randomUUID();
        GroupTxnConfirmedEvent event = new GroupTxnConfirmedEvent(
                groupTxnId,
                UUID.randomUUID(),
                GroupTxnType.CONTRIBUTION,
                1_000_000L,
                transactorId,
                null, // không có categoryCode
                Instant.now(),
                "Nộp quỹ tháng 10",
                Map.of()
        );

        groupTxnListener.onConfirmed(event);

        verify(notificationService).createNotification(
                eq(transactorId),
                eq(NotiType.GROUP_TXN_RECORD_REQUEST),
                eq("Ghi vào sổ cá nhân?"),
                any(),
                eq(groupTxnId),
                any()
        );
    }

    @Test
    @DisplayName("onConfirmed: Giao dịch hoàn tiền (REFUND) gửi thông báo cho từng người được hoàn tiền")
    void onConfirmed_refund_notificationSentToEachParticipant() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        UUID groupTxnId = UUID.randomUUID();
        GroupTxnConfirmedEvent event = new GroupTxnConfirmedEvent(
                groupTxnId,
                UUID.randomUUID(),
                GroupTxnType.REFUND,
                400_000L,
                null,
                null,
                Instant.now(),
                "Hoàn tiền thừa",
                Map.of(u1, 200_000L, u2, 200_000L)
        );

        groupTxnListener.onConfirmed(event);

        verify(notificationService).createNotification(
                eq(u1),
                eq(NotiType.GROUP_TXN_RECORD_REQUEST),
                eq("Ghi vào sổ cá nhân?"),
                any(),
                eq(groupTxnId),
                any()
        );
        verify(notificationService).createNotification(
                eq(u2),
                eq(NotiType.GROUP_TXN_RECORD_REQUEST),
                eq("Ghi vào sổ cá nhân?"),
                any(),
                eq(groupTxnId),
                any()
        );
    }

    @Test
    @DisplayName("onPending: Giao dịch chờ duyệt gửi thông báo cho thủ quỹ với type GROUP_TXN_REVIEW_REQUEST và tên người tạo")
    void onPending_reviewRequestSentWithCreatorName() {
        UUID fundKeeperId = UUID.randomUUID();
        UUID createdBy = UUID.randomUUID();
        UUID groupTxnId = UUID.randomUUID();

        when(userService.getNames(List.of(createdBy))).thenReturn(Map.of(
                createdBy, new UserNameDisplayRes(createdBy, "Văn", "A", UserStatus.ACTIVE, false)
        ));

        GroupTxnPendingEvent event = new GroupTxnPendingEvent(
                fundKeeperId,
                groupTxnId,
                UUID.randomUUID(),
                createdBy,
                GroupTxnType.EXPENSE
        );

        groupTxnListener.onPending(event);

        verify(notificationService).createNotification(
                eq(fundKeeperId),
                eq(NotiType.GROUP_TXN_REVIEW_REQUEST),
                eq("Giao dịch nhóm chờ duyệt"),
                org.mockito.ArgumentMatchers.contains("Văn A"),
                eq(groupTxnId),
                any()
        );
    }
}

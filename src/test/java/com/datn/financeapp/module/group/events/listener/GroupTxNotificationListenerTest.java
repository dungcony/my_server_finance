package com.datn.financeapp.module.group.events.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.datn.financeapp.module.category.service.CategoryService;
import com.datn.financeapp.module.group.entity.Fund;
import com.datn.financeapp.module.group.entity.Member;
import com.datn.financeapp.module.group.enums.*;
import com.datn.financeapp.module.group.events.GroupTxnConfirmedEvent;
import com.datn.financeapp.module.group.events.GroupTxnPendingEvent;
import com.datn.financeapp.module.group.repository.FundRepository;
import com.datn.financeapp.module.group.repository.MemberRepository;
import com.datn.financeapp.module.notification.enums.NotiType;
import com.datn.financeapp.module.notification.service.NotificationService;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupTxNotificationListenerTest {

    // 03:00 UTC cùng ngày với 10:00 giờ Việt Nam
    private static final Instant OCCURRED_AT = Instant.parse("2026-10-12T03:00:00Z");

    @Mock
    private NotificationService notificationService;
    @Mock
    private CategoryService categoryService;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private FundRepository fundRepository;

    @InjectMocks
    private GroupTxNotificationListener listener;

    private UUID groupId;
    private UUID txnId;
    private UUID userA;
    private UUID userB;

    @BeforeEach
    void setUp() {
        groupId = UUID.randomUUID();
        txnId = UUID.randomUUID();
        userA = UUID.randomUUID();
        userB = UUID.randomUUID();
    }

    // ---------------------------------- onConfirmed ----------------------------------//

    @Test
    @DisplayName("Hoàn tiền cho hai người: mỗi người một thông báo với đúng phần của mình, thu nhập, danh mục hệ thống")
    void onConfirmed_refund_oneNotificationPerReceiver() {
        UUID refundCategoryId = UUID.randomUUID();
        when(categoryService.findIdByCode("nhan_hoan_tien_nhom")).thenReturn(refundCategoryId);

        listener.onConfirmed(confirmed(GroupTxnType.REFUND, MoneySource.FUND, 500_000L, null, null, "hoàn quỹ",
                Map.of(userA, 300_000L, userB, 200_000L)));

        Map<String, Object> dataA = sentData(userA);
        assertThat(dataA).containsEntry("action", "RECORD_PERSONAL")
                .containsEntry("group_id", groupId.toString())
                .containsEntry("type", "INCOME")
                .containsEntry("amount", 300_000L)
                .containsEntry("category_id", refundCategoryId.toString())
                .containsEntry("date", "2026-10-12")
                .containsEntry("display_name", "Nhận hoàn tiền từ nhóm")
                .containsEntry("note", "hoàn quỹ");
        assertThat(sentData(userB)).containsEntry("amount", 200_000L);
    }

    @Test
    @DisplayName("Góp quỹ: một thông báo cho người nộp, chi tiêu ra khỏi ví, danh mục hệ thống gop_quy_nhom")
    void onConfirmed_contribution_notifiesTransactor() {
        UUID contributionCategoryId = UUID.randomUUID();
        when(categoryService.findIdByCode("gop_quy_nhom")).thenReturn(contributionCategoryId);

        listener.onConfirmed(confirmed(GroupTxnType.CONTRIBUTION, MoneySource.PERSONAL, 300_000L, userA, null,
                null, Map.of()));

        Map<String, Object> data = sentData(userA);
        assertThat(data).containsEntry("type", "EXPENSE")
                .containsEntry("amount", 300_000L)
                .containsEntry("category_id", contributionCategoryId.toString())
                .containsEntry("display_name", "Góp quỹ nhóm")
                .doesNotContainKey("note");
        verify(notificationService, times(1)).createNotification(any(), any(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("Chi tiêu bằng tiền cá nhân: danh mục lấy từ giao dịch nhóm, không tra danh mục hệ thống")
    void onConfirmed_personalExpense_usesTransactionCategory() {
        UUID expenseCategoryId = UUID.randomUUID();

        listener.onConfirmed(confirmed(GroupTxnType.EXPENSE, MoneySource.PERSONAL, 120_000L, userA,
                expenseCategoryId, null, Map.of(userA, 60_000L, userB, 60_000L)));

        assertThat(sentData(userA)).containsEntry("type", "EXPENSE")
                .containsEntry("amount", 120_000L)
                .containsEntry("category_id", expenseCategoryId.toString())
                .containsEntry("display_name", "Chi tiêu nhóm");
        verifyNoInteractions(categoryService);
    }

    @Test
    @DisplayName("Chi tiêu bằng quỹ và kiểm kê: không gửi thông báo nào")
    void onConfirmed_fundExpenseAndAdjustment_sendsNothing() {
        listener.onConfirmed(confirmed(GroupTxnType.EXPENSE, MoneySource.FUND, 100_000L, null,
                UUID.randomUUID(), null, Map.of(userA, 100_000L)));
        listener.onConfirmed(confirmed(GroupTxnType.ADJUSTMENT_UP, MoneySource.FUND, 100_000L, null,
                null, null, Map.of(userA, 100_000L)));

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("Gửi thông báo lỗi thì listener không ném ra ngoài")
    void onConfirmed_notificationFails_doesNotThrow() {
        when(categoryService.findIdByCode("gop_quy_nhom")).thenReturn(UUID.randomUUID());
        doThrow(new IllegalStateException("db down"))
                .when(notificationService).createNotification(any(), any(), anyString(), anyString(), any(), any());

        assertThatCode(() -> listener.onConfirmed(confirmed(GroupTxnType.CONTRIBUTION, MoneySource.PERSONAL,
                300_000L, userA, null, null, Map.of())))
                .doesNotThrowAnyException();
    }

    // ---------------------------------- onPending ----------------------------------//

    @Test
    @DisplayName("Báo chủ nhóm và thủ quỹ có giao dịch chờ duyệt")
    void onPending_notifiesOwnerAndTreasurer() {
        UUID creatorId = UUID.randomUUID();
        stubReviewers(userA, userB);

        listener.onPending(pending(creatorId));

        verify(notificationService).createNotification(eq(userA), eq(NotiType.GROUP_TXN_REVIEW_REQUEST),
                anyString(), anyString(), eq(txnId), any());
        verify(notificationService).createNotification(eq(userB), eq(NotiType.GROUP_TXN_REVIEW_REQUEST),
                anyString(), anyString(), eq(txnId), any());
    }

    @Test
    @DisplayName("Chủ nhóm cũng giữ quỹ thì chỉ nhận một thông báo")
    void onPending_ownerIsAlsoTreasurer_notifiedOnce() {
        stubReviewers(userA, userA);

        listener.onPending(pending(UUID.randomUUID()));

        verify(notificationService, times(1)).createNotification(eq(userA), eq(NotiType.GROUP_TXN_REVIEW_REQUEST),
                anyString(), anyString(), eq(txnId), any());
    }

    @Test
    @DisplayName("Người tạo giao dịch không bị báo lại")
    void onPending_excludesCreator() {
        stubReviewers(userA, userB);

        listener.onPending(pending(userA));

        verify(notificationService, never()).createNotification(eq(userA), any(), anyString(), anyString(), any(), any());
        verify(notificationService).createNotification(eq(userB), eq(NotiType.GROUP_TXN_REVIEW_REQUEST),
                anyString(), anyString(), eq(txnId), any());
    }

    // ---------------------------------- helper ----------------------------------//

    private GroupTxnConfirmedEvent confirmed(GroupTxnType type, MoneySource source, long amount,
                                             UUID transactorId, UUID categoryId, String note,
                                             Map<UUID, Long> shares) {
        return new GroupTxnConfirmedEvent(txnId, groupId, type, source, amount, transactorId, categoryId,
                OCCURRED_AT, note, shares);
    }

    private GroupTxnPendingEvent pending(UUID createdBy) {
        return new GroupTxnPendingEvent(txnId, groupId, createdBy, GroupTxnType.EXPENSE, MoneySource.FUND,
                100_000L);
    }

    private void stubReviewers(UUID ownerId, UUID keeperId) {
        Member owner = mock(Member.class);
        when(owner.getUserId()).thenReturn(ownerId);
        when(memberRepository.findByGroupIdAndRoleAndStatus(groupId, MemberRole.OWNER, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(owner));
        Fund fund = mock(Fund.class);
        when(fund.getKeepperId()).thenReturn(keeperId);
        when(fundRepository.findByGroupId(groupId)).thenReturn(Optional.of(fund));
    }

    // data của thông báo ghi sổ gửi cho userId
    @SuppressWarnings("unchecked")
    private Map<String, Object> sentData(UUID userId) {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).createNotification(eq(userId), eq(NotiType.GROUP_TXN_RECORD_REQUEST),
                anyString(), anyString(), eq(txnId), captor.capture());
        return captor.getValue();
    }
}

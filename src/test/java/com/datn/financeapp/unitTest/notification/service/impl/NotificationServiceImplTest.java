package com.datn.financeapp.unitTest.notification.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.notification.dto.response.NotificationListResponse;
import com.datn.financeapp.module.notification.dto.response.NotificationRes;
import com.datn.financeapp.module.notification.entity.Notification;
import com.datn.financeapp.module.notification.enums.NotiType;
import com.datn.financeapp.module.notification.mapper.NotificationMapper;
import com.datn.financeapp.module.notification.repository.NotificationRepository;
import com.datn.financeapp.module.notification.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationMapper notificationMapper;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Test
    @DisplayName("list: isRead null thi goi findAllByUserId")
    void list_IsReadNull_CallsFindAllByUserId() {
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        Notification notif = Notification.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .type(NotiType.BUDGET_ALERT)
                .title("Alert")
                .content("Spent 80%")
                .isRead(false)
                .createdAt(Instant.now())
                .build();
        NotificationRes res = new NotificationRes(notif.getId(), NotiType.BUDGET_ALERT, "Alert", "Spent 80%", null, null, false, Instant.now());
        Page<Notification> page = new PageImpl<>(List.of(notif), pageable, 1);

        when(notificationRepository.findAllByUserId(userId, pageable)).thenReturn(page);
        when(notificationMapper.toRes(notif)).thenReturn(res);

        NotificationListResponse listRes = notificationService.list(userId, null, pageable);

        assertThat(listRes).isNotNull();
        assertThat(listRes.items()).hasSize(1);
        assertThat(listRes.meta().totalItems()).isEqualTo(1L);
        verify(notificationRepository, never()).findAllByUserIdAndIsRead(any(), any(), any());
    }

    @Test
    @DisplayName("list: isRead != null thi goi findAllByUserIdAndIsRead")
    void list_IsReadNotNull_CallsFindAllByUserIdAndIsRead() {
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        Page<Notification> page = new PageImpl<>(List.of(), pageable, 0);

        when(notificationRepository.findAllByUserIdAndIsRead(userId, false, pageable)).thenReturn(page);

        NotificationListResponse listRes = notificationService.list(userId, false, pageable);

        assertThat(listRes).isNotNull();
        assertThat(listRes.items()).isEmpty();
        verify(notificationRepository, never()).findAllByUserId(any(), any());
    }

    @Test
    @DisplayName("markRead: Khong tim thay thong bao cua user -> nem NOT_FOUND")
    void markRead_NotFound_ThrowsException() {
        UUID userId = UUID.randomUUID();
        UUID notifId = UUID.randomUUID();

        when(notificationRepository.findByIdForUser(notifId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markRead(userId, notifId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.NOT_FOUND.getCode());

        verify(notificationRepository, never()).markRead(any(), any());
    }

    @Test
    @DisplayName("markRead: Tim thay thong bao -> danh dau da doc thanh cong")
    void markRead_Found_Success() {
        UUID userId = UUID.randomUUID();
        UUID notifId = UUID.randomUUID();
        Notification notif = Notification.builder().id(notifId).userId(userId).build();

        when(notificationRepository.findByIdForUser(notifId, userId)).thenReturn(Optional.of(notif));

        notificationService.markRead(userId, notifId);

        verify(notificationRepository).markRead(notifId, userId);
    }

    @Test
    @DisplayName("createBudgetAlert: Goi insertBudgetAlertIfNotExists")
    void createBudgetAlert_DelegatesToRepository() {
        UUID userId = UUID.randomUUID();
        UUID budgetId = UUID.randomUUID();

        notificationService.createBudgetAlert(userId, "Cảnh báo", "Vượt 80%", budgetId);

        verify(notificationRepository).insertBudgetAlertIfNotExists(userId, "Cảnh báo", "Vượt 80%", budgetId);
    }

    @Test
    @DisplayName("createDebtReminder: Goi insertDebtReminderIfNotExists")
    void createDebtReminder_DelegatesToRepository() {
        UUID userId = UUID.randomUUID();
        UUID debtId = UUID.randomUUID();

        notificationService.createDebtReminder(userId, "Nhắc nợ", "Đến hạn", debtId);

        verify(notificationRepository).insertDebtReminderIfNotExists(userId, "Nhắc nợ", "Đến hạn", debtId);
    }

    @Test
    @DisplayName("createNotification: Tao thong bao tong quat kem data payload")
    void createNotification_WithData_SavesEntity() {
        UUID userId = UUID.randomUUID();
        UUID refId = UUID.randomUUID();
        Map<String, Object> data = Map.of("group_id", UUID.randomUUID().toString());

        notificationService.createNotification(userId, NotiType.GROUP_TXN_RECORD_REQUEST, "Nhóm", "Chia tiền", refId, data);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getType()).isEqualTo(NotiType.GROUP_TXN_RECORD_REQUEST);
        assertThat(saved.getTitle()).isEqualTo("Nhóm");
        assertThat(saved.getContent()).isEqualTo("Chia tiền");
        assertThat(saved.getReferenceId()).isEqualTo(refId);
        assertThat(saved.getData()).isEqualTo(data);
        assertThat(saved.getIsRead()).isFalse();
        assertThat(saved.getCreatedAt()).isNotNull();
    }
}

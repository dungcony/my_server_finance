package com.datn.financeapp.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.notification.entity.Notification;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.enums.UserPlan;
import com.datn.financeapp.user.enums.UserStatus;
import com.datn.financeapp.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Transactional
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class NotificationRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1ub3RpZi1yZXBvLXRlc3QtMzJi");
    }

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private UUID userId;
    private UUID otherUserId;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        userRepository.deleteAll();

        User user = userRepository.saveAndFlush(User.builder()
                .id(UUID.randomUUID())
                .email("notif.user@example.com")
                .password("hash")
                .firstName("Notif")
                .lastName("Owner")
                .plan(UserPlan.FREE)
                .status(UserStatus.ACTIVE)
                .isDeleted(false)
                .createdAt(Instant.now())
                .build());
        userId = user.getId();

        User otherUser = userRepository.saveAndFlush(User.builder()
                .id(UUID.randomUUID())
                .email("other.notif@example.com")
                .password("hash")
                .firstName("Other")
                .lastName("User")
                .plan(UserPlan.FREE)
                .status(UserStatus.ACTIVE)
                .isDeleted(false)
                .createdAt(Instant.now())
                .build());
        otherUserId = otherUser.getId();
    }

    private Notification createNotification(UUID ownerId, String type, String title, String content, boolean read) {
        Notification notif = Notification.builder()
                .id(UUID.randomUUID())
                .userId(ownerId)
                .type(type)
                .title(title)
                .content(content)
                .isRead(read)
                .createdAt(Instant.now())
                .build();
        return notificationRepository.saveAndFlush(notif);
    }

    @Test
    @DisplayName("findByIdForUser: Chỉ trả về thông báo thuộc quyền sở hữu của user (D-27 isolation)")
    void findByIdForUser_EnforcesOwnership() {
        Notification n = createNotification(userId, "budget_alert", "Welcome", "Welcome to the app", false);

        Optional<Notification> found = notificationRepository.findByIdForUser(n.getId(), userId);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(n.getId());

        // User khác không thấy (D-27 -> 404)
        assertThat(notificationRepository.findByIdForUser(n.getId(), otherUserId)).isEmpty();
    }

    @Test
    @DisplayName("findAllForUser & countForUser: Lọc theo isRead và phân trang")
    void findAllAndCountForUser_FiltersAndPaginates() {
        createNotification(userId, "budget_alert", "Msg 1", "Unread 1", false);
        createNotification(userId, "budget_alert", "Msg 2", "Unread 2", false);
        createNotification(userId, "budget_alert", "Msg 3", "Read 1", true);

        long unreadCount = notificationRepository.countForUser(userId, false);
        assertThat(unreadCount).isEqualTo(2);

        List<Notification> unreadList = notificationRepository.findAllForUser(userId, false, 10, 0);
        assertThat(unreadList).hasSize(2);
    }

    @Test
    @DisplayName("markRead: Đánh dấu đã đọc cho thông báo")
    void markRead_UpdatesStatus() {
        Notification n = createNotification(userId, "budget_alert", "Test", "Content", false);

        int updated = notificationRepository.markRead(n.getId(), userId);
        assertThat(updated).isEqualTo(1);

        entityManager.clear();
        Notification reloaded = notificationRepository.findById(n.getId()).orElseThrow();
        assertThat(reloaded.getIsRead()).isTrue();
    }

    @Test
    @DisplayName("insertBudgetAlertIfNotExists: Chèn thông báo cảnh báo và chống trùng lặp ON CONFLICT")
    void insertBudgetAlertIfNotExists_InsertsAndIgnoresDuplicates() {
        UUID budgetId = UUID.randomUUID();

        // Lần đầu chèn thành công
        notificationRepository.insertBudgetAlertIfNotExists(userId, "Cảnh báo 80%", "Bạn đã tiêu 80%", budgetId);
        long count1 = notificationRepository.countForUser(userId, null);
        assertThat(count1).isEqualTo(1);

        // Lần hai cùng user, cùng ngày, cùng budgetId -> DO NOTHING
        notificationRepository.insertBudgetAlertIfNotExists(userId, "Cảnh báo 80%", "Bạn đã tiêu 80%", budgetId);
        long count2 = notificationRepository.countForUser(userId, null);
        assertThat(count2).isEqualTo(1);
    }
}

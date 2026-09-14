package com.datn.financeapp.report.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.report.entity.ExportJob;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.enums.UserPlan;
import com.datn.financeapp.user.enums.UserStatus;
import com.datn.financeapp.user.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class ExportJobRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1leHBvcnQtcmVwby10ZXN0");
    }

    @Autowired
    private ExportJobRepository exportJobRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private UUID userId;
    private UUID otherUserId;

    @BeforeEach
    void setUp() {
        exportJobRepository.deleteAll();
        userRepository.deleteAll();

        User user = userRepository.saveAndFlush(User.builder()
                .id(UUID.randomUUID())
                .email("export.user@example.com")
                .password("hash")
                .firstName("Export")
                .lastName("Owner")
                .plan(UserPlan.FREE)
                .status(UserStatus.ACTIVE)
                .isDeleted(false)
                .createdAt(Instant.now())
                .build());
        userId = user.getId();

        User otherUser = userRepository.saveAndFlush(User.builder()
                .id(UUID.randomUUID())
                .email("other.export@example.com")
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

    private ExportJob createJob(UUID ownerId, String format, String status, Instant expiresAt, Instant createdAt) {
        ExportJob job = ExportJob.builder()
                .id(UUID.randomUUID())
                .userId(ownerId)
                .format(format)
                .status(status)
                .expiresAt(expiresAt)
                .createdAt(createdAt != null ? createdAt : Instant.now())
                .build();
        return exportJobRepository.saveAndFlush(job);
    }

    @Test
    @DisplayName("findByIdAndUserId: Chỉ trả về tác vụ xuất của chính user (chặn IDOR)")
    void findByIdAndUserId_EnforcesOwnership() {
        ExportJob job = createJob(userId, "csv", "processing", null, null);

        Optional<ExportJob> found = exportJobRepository.findByIdAndUserId(job.getId(), userId);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(job.getId());

        // User khác không thấy
        assertThat(exportJobRepository.findByIdAndUserId(job.getId(), otherUserId)).isEmpty();
    }

    @Test
    @DisplayName("markCompleted & markFailed: Cập nhật trạng thái tác vụ")
    void markCompletedAndFailed_UpdatesStatus() {
        ExportJob job1 = createJob(userId, "csv", "processing", null, null);
        Instant expires = Instant.now().plus(1, ChronoUnit.HOURS);
        exportJobRepository.markCompleted(job1.getId(), "/tmp/test.csv", expires);

        entityManager.clear();
        ExportJob reloaded1 = exportJobRepository.findById(job1.getId()).orElseThrow();
        assertThat(reloaded1.getStatus()).isEqualTo("completed");
        assertThat(reloaded1.getFilePath()).isEqualTo("/tmp/test.csv");

        ExportJob job2 = createJob(userId, "csv", "processing", null, null);
        exportJobRepository.markFailed(job2.getId(), "Disk full");

        entityManager.clear();
        ExportJob reloaded2 = exportJobRepository.findById(job2.getId()).orElseThrow();
        assertThat(reloaded2.getStatus()).isEqualTo("failed");
        assertThat(reloaded2.getErrorMessage()).isEqualTo("Disk full");
    }

    @Test
    @DisplayName("findExpired: Tìm các tác vụ completed đã hết hạn tải về")
    void findExpired_FindsExpiredJobs() {
        Instant past = Instant.now().minus(10, ChronoUnit.MINUTES);
        Instant future = Instant.now().plus(1, ChronoUnit.HOURS);

        ExportJob expired = createJob(userId, "csv", "completed", past, null);
        createJob(userId, "csv", "completed", future, null);

        List<ExportJob> expiredList = exportJobRepository.findExpired(Instant.now());
        assertThat(expiredList).extracting(ExportJob::getId).contains(expired.getId());
    }

    @Test
    @DisplayName("findStuckProcessing: Tìm các tác vụ kẹt ở trạng thái processing quá lâu")
    void findStuckProcessing_FindsOldProcessingJobs() {
        Instant twoHoursAgo = Instant.now().minus(2, ChronoUnit.HOURS);
        ExportJob stuck = createJob(userId, "csv", "processing", null, twoHoursAgo);
        createJob(userId, "csv", "processing", null, Instant.now()); // mới tạo

        List<ExportJob> stuckList = exportJobRepository.findStuckProcessing(Instant.now().minus(1, ChronoUnit.HOURS));
        assertThat(stuckList).extracting(ExportJob::getId).contains(stuck.getId());
    }
}

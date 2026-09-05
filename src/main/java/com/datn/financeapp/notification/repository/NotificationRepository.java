package com.datn.financeapp.notification.repository;

import com.datn.financeapp.notification.entity.Notification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository JPA cho {@link Notification}. Mọi method chọn/sửa MỘT thông báo theo id phải kèm
 * điều kiện quyền D-27 {@code user_id = :currentUser} ngay trong SQL (KHÔNG có vế
 * {@code group_id} — thông báo luôn là dữ liệu cá nhân, không có khái niệm thông báo chung của
 * nhóm ở Phase 4).
 */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query(
            value = "SELECT * FROM notifications WHERE user_id = :currentUser "
                    + "AND (CAST(:isRead AS boolean) IS NULL OR is_read = :isRead) "
                    + "ORDER BY created_at DESC LIMIT :limit OFFSET :offset",
            nativeQuery = true)
    List<Notification> findAllForUser(
            @Param("currentUser") UUID currentUser,
            @Param("isRead") Boolean isRead,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Query(
            value = "SELECT COUNT(*) FROM notifications WHERE user_id = :currentUser "
                    + "AND (CAST(:isRead AS boolean) IS NULL OR is_read = :isRead)",
            nativeQuery = true)
    long countForUser(@Param("currentUser") UUID currentUser, @Param("isRead") Boolean isRead);

    @Query(
            value = "SELECT * FROM notifications WHERE id = :id AND user_id = :currentUser",
            nativeQuery = true)
    Optional<Notification> findByIdForUser(@Param("id") UUID id, @Param("currentUser") UUID currentUser);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = TRUE WHERE n.id = :id AND n.userId = :currentUser")
    int markRead(@Param("id") UUID id, @Param("currentUser") UUID currentUser);

    /**
     * Dùng cho Plan 02 (budget/) — chèn cảnh báo ngân sách, bỏ qua nếu đã tồn tại bản ghi trùng
     * (user, reference, ngày, loại) theo {@code uq_notif_budget_alert} (V9). Chưa code logic
     * ngân sách ở đây, chỉ chuẩn bị sẵn method này vì Plan 02 phụ thuộc
     * {@code NotificationRepository}.
     *
     * <p>Lưu ý cú pháp — hai điều chỉnh thực tế so với bản nháp ban đầu ở Task 1 của plan:
     * <ol>
     *   <li>{@code uq_notif_budget_alert} là UNIQUE index ĐẦY ĐỦ (không có mệnh đề
     *       {@code WHERE type = 'budget_alert'}) — mệnh đề {@code WHERE} gắn vào partial unique
     *       index không tương thích ổn định với {@code ON CONFLICT} qua native query của Spring
     *       Data JPA (driver phải khai lại đúng biểu thức WHERE, dễ lệch câu chữ).</li>
     *   <li>Biểu thức ngày trong index/ON CONFLICT là {@code (created_at AT TIME ZONE 'UTC')::date}
     *       thay vì {@code created_at::date} trần — Postgres từ chối cast trực tiếp trong index
     *       expression ("functions in index expression must be marked IMMUTABLE") vì phép cast
     *       phụ thuộc timezone session. Quy về UTC cố định là IMMUTABLE.</li>
     * </ol>
     */
    @Modifying
    @Query(
            value = "INSERT INTO notifications (id, user_id, type, title, content, reference_id) "
                    + "VALUES (gen_random_uuid(), :userId, 'budget_alert', :title, :content, :budgetId) "
                    + "ON CONFLICT (user_id, reference_id, ((created_at AT TIME ZONE 'UTC')::date), type) DO NOTHING",
            nativeQuery = true)
    void insertBudgetAlertIfNotExists(
            @Param("userId") UUID userId,
            @Param("title") String title,
            @Param("content") String content,
            @Param("budgetId") UUID budgetId);

    /**
     * JOB-04 — nhắc nợ, bỏ qua nếu trong CÙNG NGÀY đã có bản ghi trùng (user, khoản nợ, loại).
     *
     * <p>Trước đây job này dùng {@link #insertGenericNotification} với lập luận "chỉ gọi đúng ba
     * mốc ngày cố định nên không thể trùng". Lập luận đó sai: mốc ngày cố định chỉ đảm bảo job
     * gọi tới một lần MỖI LẦN CHẠY, không đảm bảo job chỉ chạy một lần mỗi ngày. Deploy lại,
     * retry sau lỗi, hay chạy hai instance đều khiến cùng một khoản nợ ở cùng một mốc sinh hai
     * thông báo y hệt nhau trong hộp thư người dùng.
     *
     * <p>Không cần migration mới: {@code uq_notif_budget_alert} (V9) cố ý là UNIQUE ĐẦY ĐỦ trên
     * mọi {@code type}, không phải partial index riêng cho {@code budget_alert} — chính V9 đã ghi
     * rõ ràng buộc này áp dụng hợp lý cho cả {@code debt_reminder}. Ở đây chỉ là dùng tới nó.
     */
    @Modifying
    @Query(
            value = "INSERT INTO notifications (id, user_id, type, title, content, reference_id) "
                    + "VALUES (gen_random_uuid(), :userId, 'debt_reminder', :title, :content, :debtId) "
                    + "ON CONFLICT (user_id, reference_id, ((created_at AT TIME ZONE 'UTC')::date), type) DO NOTHING",
            nativeQuery = true)
    void insertDebtReminderIfNotExists(
            @Param("userId") UUID userId,
            @Param("title") String title,
            @Param("content") String content,
            @Param("debtId") UUID debtId);

    /**
     * Chèn thông báo dạng chung — còn dùng cho JOB-02 ({@code budget_renewed}) và
     * {@code recurring_generated}/{@code goal_completed}. Khác hai method ở trên: không có
     * {@code ON CONFLICT}, vì chống trùng của những loại này nằm ở tầng service (kiểm tra kỳ mới
     * đã tồn tại hay chưa trước khi gọi tới) chứ không dựa vào {@code uq_notif_budget_alert}.
     */
    @Modifying
    @Query(
            value = "INSERT INTO notifications (id, user_id, type, title, content, reference_id) "
                    + "VALUES (gen_random_uuid(), :userId, :type, :title, :content, :referenceId)",
            nativeQuery = true)
    void insertGenericNotification(
            @Param("userId") UUID userId,
            @Param("type") String type,
            @Param("title") String title,
            @Param("content") String content,
            @Param("referenceId") UUID referenceId);
}

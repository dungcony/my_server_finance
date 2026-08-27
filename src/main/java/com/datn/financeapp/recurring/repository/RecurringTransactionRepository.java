package com.datn.financeapp.recurring.repository;

import com.datn.financeapp.recurring.entity.RecurringTransaction;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository JPA cho {@link RecurringTransaction}. Điều kiện quyền nằm NGAY TRONG SQL (CLAUDE.md
 * §7) — không "lấy hết rồi lọc ở Java". Không có quyền thì query trả rỗng để service ném 404 chứ
 * không phải 403 (T-04-13: không để lộ việc bản ghi có tồn tại hay không).
 *
 * <p><b>Phạm vi:</b> khoản định kỳ là dữ liệu cá nhân thuần tuý — {@code recurring_transactions}
 * không có cột {@code group_id} trong schema V2, nên KHÔNG có vế nhóm gia đình ở đây (khác
 * {@code WalletRepository}).
 *
 * <p><b>{@code CAST(:param AS ...)} trong các bộ lọc tuỳ chọn:</b> PostgreSQL không suy được kiểu
 * của tham số khi nó chỉ xuất hiện ở vế {@code IS NULL}, và sẽ ném
 * {@code could not determine data type of parameter}. Cùng kỹ thuật đã dùng ở
 * {@code SavingsGoalRepository.findAllForUser}.
 */
public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, UUID> {

    @Query(
            value = "SELECT * FROM recurring_transactions WHERE id = :id AND user_id = :currentUser",
            nativeQuery = true)
    Optional<RecurringTransaction> findByIdAndUserId(
            @Param("id") UUID id, @Param("currentUser") UUID currentUser);

    @Query(
            value = "SELECT * FROM recurring_transactions WHERE user_id = :currentUser "
                    + "AND (CAST(:isEnabled AS boolean) IS NULL OR is_enabled = CAST(:isEnabled AS boolean)) "
                    + "AND (CAST(:type AS text) IS NULL OR type = CAST(:type AS text)) "
                    + "ORDER BY next_run_date",
            nativeQuery = true)
    List<RecurringTransaction> findAllForUser(
            @Param("currentUser") UUID currentUser,
            @Param("isEnabled") Boolean isEnabled,
            @Param("type") String type);

    /**
     * Các khoản tới hạn cho tác vụ nền (api/09 mục A4). Điều kiện {@code is_enabled = TRUE} viết
     * đúng dạng để chỉ mục riêng phần {@code idx_rec_due ... WHERE is_enabled} phát huy tác dụng.
     *
     * <p><b>So với đặc tả, vế {@code end_date} đối chiếu với {@code next_run_date} chứ KHÔNG phải
     * với hôm nay.</b> api/09 viết {@code end_date >= hôm nay}, nhưng điều kiện đó bỏ sót một ca
     * thật: khoản kết thúc tháng trước mà người dùng vắng mặt từ trước đó nữa vẫn còn vài kỳ CHƯA
     * ghi nằm TRƯỚC {@code end_date}. Lọc theo hôm nay thì những kỳ đó biến mất vĩnh viễn — đúng
     * kiểu sai mà D-50 cảnh báo: số dư thiếu vài kỳ nhưng vẫn "trông hợp lý".
     *
     * <p>Đối chiếu với {@code next_run_date} lấy đủ các khoản còn nợ kỳ; vòng lặp catch-up trong
     * {@code RecurringPeriodWriter} vẫn cắt đúng tại {@code end_date} nên không kỳ nào vượt quá hạn
     * người dùng đặt.
     */
    @Query(
            value = "SELECT * FROM recurring_transactions WHERE is_enabled = TRUE "
                    + "AND next_run_date <= :today AND (end_date IS NULL OR end_date >= next_run_date)",
            nativeQuery = true)
    List<RecurringTransaction> findDue(@Param("today") LocalDate today);
}

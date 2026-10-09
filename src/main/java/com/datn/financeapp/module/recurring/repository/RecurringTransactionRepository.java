package com.datn.financeapp.module.recurring.repository;

import com.datn.financeapp.module.recurring.entity.RecurringTransaction;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository thao tác cơ sở dữ liệu cho {@link RecurringTransaction}.
 * Cung cấp các phương thức truy vấn danh sách, chi tiết và quét các khoản đến hạn như {@link #findDue(LocalDate)},
 * {@link #findByIdAndUserId(UUID, UUID)} và {@link #findAllForUser(UUID, Boolean, String)}.
 */
public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, UUID> {

    /**
     * Tìm khoản giao dịch định kỳ theo ID và thuộc về người dùng hiện tại.
     *
     * @param id          ID giao dịch định kỳ
     * @param currentUser ID người dùng hiện tại
     * @return {@link Optional} chứa giao dịch định kỳ nếu tìm thấy và thuộc về người dùng
     */
    @Query(
            value = "SELECT * FROM recurring_transactions WHERE id = :id AND user_id = :currentUser",
            nativeQuery = true)
    Optional<RecurringTransaction> findByIdAndUserId(
            @Param("id") UUID id, @Param("currentUser") UUID currentUser);

    /**
     * Tìm danh sách các giao dịch định kỳ của người dùng theo bộ lọc.
     *
     * @param currentUser ID người dùng hiện tại
     * @param isEnabled   lọc theo trạng thái bật/tắt (null nếu không lọc)
     * @param type        lọc theo loại giao dịch (INCOME/EXPENSE, null nếu không lọc)
     * @return danh sách các giao dịch định kỳ thỏa mãn điều kiện
     */
    @Query(
            value = """
                    SELECT * FROM recurring_transactions WHERE user_id = :currentUser
                    AND (CAST(:isEnabled AS boolean) IS NULL OR is_enabled = CAST(:isEnabled AS boolean))
                    AND (CAST(:type AS text) IS NULL OR type = CAST(:type AS text))
                    ORDER BY next_run_date
                    """,
            nativeQuery = true)
    List<RecurringTransaction> findAllForUser(
            @Param("currentUser") UUID currentUser,
            @Param("isEnabled") Boolean isEnabled,
            @Param("type") String type);

    /**
     * Tìm các giao dịch định kỳ đến hạn chạy (next_run_date <= today).
     *
     * @param today ngày hiện tại dùng làm mốc so sánh
     * @return danh sách các giao dịch định kỳ cần xử lý phát sinh
     */
    @Query(
            value = """
                    SELECT * FROM recurring_transactions WHERE is_enabled = TRUE
                    AND next_run_date <= :today AND (end_date IS NULL OR end_date >= next_run_date)
                    """,
            nativeQuery = true)
    List<RecurringTransaction> findDue(@Param("today") LocalDate today);
}

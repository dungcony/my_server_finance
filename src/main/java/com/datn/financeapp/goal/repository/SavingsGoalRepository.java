package com.datn.financeapp.goal.repository;

import com.datn.financeapp.goal.entity.SavingsGoal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository JPA cho {@link SavingsGoal}. Điều kiện quyền nằm NGAY TRONG SQL (CLAUDE.md §7) —
 * không "lấy hết rồi lọc ở Java". Không có quyền thì query trả rỗng để service ném 404, không
 * phải 403 (T-04-12: không để lộ việc bản ghi có tồn tại hay không).
 *
 * <p><b>Phạm vi Phase 4:</b> mục tiêu tiết kiệm là dữ liệu cá nhân thuần tuý —
 * {@code savings_goals} không có cột {@code group_id} trong schema V4, nên KHÔNG có vế nhóm gia
 * đình ở đây (khác {@code WalletRepository}).
 */
public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, UUID> {

    @Query(value = "SELECT * FROM savings_goals WHERE id = :id AND user_id = :currentUser", nativeQuery = true)
    Optional<SavingsGoal> findByIdAndUserId(@Param("id") UUID id, @Param("currentUser") UUID currentUser);

    @Query(
            value = "SELECT * FROM savings_goals WHERE user_id = :currentUser "
                    + "AND (CAST(:status AS text) IS NULL OR status = :status) "
                    + "ORDER BY created_at DESC",
            nativeQuery = true)
    List<SavingsGoal> findAllForUser(@Param("currentUser") UUID currentUser, @Param("status") String status);

    /**
     * Đọc lại tiến độ mục tiêu SAU khi trigger {@code trg_goal_contributions_sync} đã UPDATE bảng
     * cha.
     *
     * <p><b>Vì sao trả SCALAR chứ không trả entity {@link SavingsGoal}:</b> native query trả về
     * entity vẫn đi qua Hibernate identity map — nếu service đã load cùng bản ghi đó trước trong
     * CÙNG transaction (bước kiểm tra quyền), Hibernate sẽ trả lại ĐÚNG instance đã cache, mang
     * {@code saved_amount}/{@code status} CŨ trước khi trigger chạy, và câu SELECT mới trở thành
     * vô nghĩa. Lỗi này đã xảy ra thật ở Plan 03 với {@code DebtRepository.findByIdNative}: lần
     * trả thứ hai báo {@code paid_amount} bằng đúng lần trả thứ nhất.
     *
     * <p>Trả về scalar thì Hibernate không hydrate qua identity map, nên luôn là giá trị mới nhất
     * dưới CSDL — cùng nguyên lý với {@code WalletRepository.findCurrentBalanceNative} và
     * {@code DebtRepository.findPaidAmountNative}.
     *
     * <p>KHÔNG có điều kiện quyền — cố ý tách khỏi {@link #findByIdAndUserId}. Chỉ dùng khi service
     * ĐÃ kiểm tra quyền ở bước load trước đó và giờ chỉ cần giá trị mới nhất.
     */
    @Query(value = "SELECT saved_amount FROM savings_goals WHERE id = :id", nativeQuery = true)
    Optional<Long> findSavedAmountNative(@Param("id") UUID id);

    // Cặp đôi với {@link #findSavedAmountNative} — cùng lý do trả scalar.
    @Query(value = "SELECT status FROM savings_goals WHERE id = :id", nativeQuery = true)
    Optional<String> findStatusNative(@Param("id") UUID id);
}

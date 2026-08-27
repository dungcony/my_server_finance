package com.datn.financeapp.debt.repository;

import com.datn.financeapp.debt.entity.Debt;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository JPA cho {@link Debt}. Điều kiện quyền nằm NGAY TRONG SQL (CLAUDE.md §7) — không
 * "lấy hết rồi lọc ở Java". Không có quyền thì query trả rỗng để service ném 404, không phải 403
 * (T-04-09: không để lộ việc bản ghi có tồn tại hay không).
 *
 * <p><b>Phạm vi Phase 4:</b> sổ nợ là dữ liệu cá nhân thuần tuý — {@code debts} không có cột
 * {@code group_id} trong schema V4, nên KHÔNG có vế nhóm gia đình ở đây (khác
 * {@code WalletRepository}). Chủ nhóm cũng không có ngoại lệ nào để xem sổ nợ của thành viên.
 */
public interface DebtRepository extends JpaRepository<Debt, UUID> {

    @Query(value = "SELECT * FROM debts WHERE id = :id AND user_id = :currentUser", nativeQuery = true)
    Optional<Debt> findByIdAndUserId(@Param("id") UUID id, @Param("currentUser") UUID currentUser);

    @Query(
            value = "SELECT * FROM debts WHERE user_id = :currentUser "
                    + "AND (CAST(:type AS text) IS NULL OR type = :type) "
                    + "AND (CAST(:status AS text) IS NULL OR status = :status) "
                    + "ORDER BY issued_date DESC, created_at DESC",
            nativeQuery = true)
    List<Debt> findAllForUser(
            @Param("currentUser") UUID currentUser, @Param("type") String type, @Param("status") String status);

    /**
     * Đọc lại tiến độ khoản nợ SAU khi trigger {@code trg_debt_payments_sync} đã UPDATE bảng cha.
     *
     * <p><b>Vì sao trả SCALAR chứ không trả entity {@link Debt}:</b> native query trả về entity
     * vẫn đi qua Hibernate identity map — nếu service đã load cùng bản ghi đó trước trong CÙNG
     * transaction (bước kiểm tra quyền), Hibernate sẽ trả lại ĐÚNG instance đã cache, mang
     * {@code paid_amount}/{@code status} CŨ trước khi trigger chạy, và câu SELECT mới trở thành vô
     * nghĩa. Đã dính đúng lỗi này khi chạy test thật: lần trả thứ hai báo paid_amount = 1.000.000
     * thay vì 2.000.000.
     *
     * <p>Trả về scalar thì Hibernate không hydrate qua identity map, nên luôn là giá trị mới nhất
     * dưới CSDL — đúng nguyên lý đã dùng ở {@code WalletRepository.findCurrentBalanceNative}
     * (cũng trả scalar, cũng vì lý do này).
     *
     * <p>KHÔNG có điều kiện quyền — cố ý tách khỏi {@link #findByIdAndUserId}. Chỉ dùng khi service
     * ĐÃ kiểm tra quyền ở bước load trước đó và giờ chỉ cần giá trị mới nhất.
     */
    @Query(value = "SELECT paid_amount FROM debts WHERE id = :id", nativeQuery = true)
    Optional<Long> findPaidAmountNative(@Param("id") UUID id);

    /** Cặp đôi với {@link #findPaidAmountNative} — cùng lý do trả scalar. */
    @Query(value = "SELECT status FROM debts WHERE id = :id", nativeQuery = true)
    Optional<String> findStatusNative(@Param("id") UUID id);

    /**
     * DEBT-07/JOB-04 — nguồn dữ liệu cho tác vụ nhắc nợ đến hạn (api/08 mục 9). Plan này chỉ cung
     * cấp phần TRUY VẤN; job {@code @Scheduled} thật gộp chung ở Plan 07 theo D-57.
     */
    @Query(
            value = "SELECT * FROM debts WHERE user_id = :currentUser AND status = 'outstanding' "
                    + "AND due_date IS NOT NULL ORDER BY due_date",
            nativeQuery = true)
    List<Debt> findOutstandingWithDueDate(@Param("currentUser") UUID currentUser);
}

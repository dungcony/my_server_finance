package com.datn.financeapp.module.debt.repository;

import com.datn.financeapp.module.debt.dto.response.DebtReminderView;
import com.datn.financeapp.module.debt.entity.Debt;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository JPA cho {@link Debt}. Điều kiện quyền nằm NGAY TRONG SQL.
 * Lọc các bản ghi chưa bị xóa mềm (NOT is_deleted).
 */
public interface DebtRepository extends JpaRepository<Debt, UUID>, JpaSpecificationExecutor<Debt> {
    Optional<Debt> findByIdAndIsDeletedFalseAndUserId(UUID id, UUID userId);

    Optional<Debt> findByIdAndIsDeletedFalse(UUID id);

    /**
     * Quét toàn hệ thống — chỉ lấy các khoản nợ ĐANG XỬ LÝ mà HÔM NAY thực sự cần nhắc:
     * <ul>
     *   <li>Còn đúng 7 ngày đến hạn</li>
     *   <li>Còn đúng 1 ngày đến hạn</li>
     *   <li>Đến hạn hôm nay hoặc quá hạn và số ngày chia hết cho 7</li>
     * </ul>
     * Trả về projection tối giản — không load full entity vào Hibernate context.
     */
    @Query("""
            SELECT d.id AS id, d.userId AS userId, d.name AS name, d.dueDate AS dueDate
            FROM Debt d
            WHERE d.status = com.datn.financeapp.module.debt.enums.DebtStatus.INPROCESS
              AND d.dueDate IS NOT NULL
              AND d.isDeleted = false
              AND (
                  d.dueDate = :today + 7
                  OR d.dueDate = :today + 1
                  OR (d.dueDate <= :today AND MOD(DATEDIFF(:today, d.dueDate), 7) = 0)
              )
            """)
    List<DebtReminderView> findAllDueRemindersForToday(LocalDate today);
}

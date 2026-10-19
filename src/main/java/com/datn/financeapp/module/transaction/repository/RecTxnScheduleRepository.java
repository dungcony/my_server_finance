package com.datn.financeapp.module.transaction.repository;

import com.datn.financeapp.module.transaction.entity.RecTxnSchedule;
import com.datn.financeapp.module.transaction.enums.RecTxnScheduleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Quản lý truy vấn dữ liệu cho bảng lịch phát sinh {@code recurring_transaction_schedules}.
 *
 * @see RecTxnSchedule
 */
public interface RecTxnScheduleRepository extends JpaRepository<RecTxnSchedule, UUID> {

    /**
     * Tìm danh sách phân trang các buổi lịch thuộc một giao dịch định kỳ.
     *
     * @param recId    ID giao dịch định kỳ cha
     * @param pageable thông tin phân trang và sắp xếp
     * @return trang danh sách các buổi lịch chưa bị xóa
     */
    Page<RecTxnSchedule> findByRecTxnIdAndIsDeletedFalse(UUID recId, Pageable pageable);

    /**
     * Tìm thông tin buổi lịch cụ thể theo ID và quyền sở hữu của người dùng.
     *
     * @param id     ID buổi lịch
     * @param userId ID người dùng sở hữu
     * @return {@link Optional} chứa buổi lịch nếu tìm thấy và chưa bị xóa
     */
    Optional<RecTxnSchedule> findByIdAndUserIdAndIsDeletedFalse(UUID id, UUID userId);

    Optional<RecTxnSchedule> findByIdAndIsDeletedFalse(UUID id);

    /**
     * Tìm các buổi lịch đến hạn chạy tự động phục vụ Scheduler engine.
     *
     * @param status trạng thái cần quét (thường là PENDING)
     * @param now    mốc thời gian hiện tại
     * @return danh sách các buổi lịch cần xử lý
     */
    List<RecTxnSchedule> findByStatusAndRunAtLessThanEqualAndIsDeletedFalse(RecTxnScheduleStatus status, Instant now);

    /**
     * Tìm buổi lịch có mốc thời gian chạy xa nhất trong tương lai của một giao dịch định kỳ.
     *
     * @param recId ID giao dịch định kỳ cha
     * @return {@link Optional} chứa buổi lịch xa nhất
     */
    Optional<RecTxnSchedule> findTopByRecTxnIdAndIsDeletedFalseOrderByRunAtDesc(UUID recId);

    /**
     * Đếm số lượng buổi lịch theo trạng thái của một giao dịch định kỳ.
     *
     * @param recId  ID giao dịch định kỳ cha
     * @param status trạng thái cần đếm
     * @return số lượng bản ghi thỏa mãn
     */
    long countByRecTxnIdAndStatusAndIsDeletedFalse(UUID recId, RecTxnScheduleStatus status);

    Page<RecTxnSchedule> findByUserIdAndRecTxnIdAndIsDeletedFalse(UUID userId, UUID recId, Pageable pageable);

    /**
     * Xoá hẳn các buổi chưa chạy: mọi buổi PENDING và buổi SKIPPED còn ở tương lai.
     *
     * @param recId ID giao dịch định kỳ cha
     * @return số buổi đã xoá
     */
    @Modifying
    @Query("""
            DELETE FROM RecTxnSchedule s
            WHERE s.recTxn.id = :recId
              AND (s.status = com.datn.financeapp.module.transaction.enums.RecTxnScheduleStatus.PENDING
                   OR (s.status = com.datn.financeapp.module.transaction.enums.RecTxnScheduleStatus.SKIPPED
                       AND s.runAt > CURRENT_TIMESTAMP))
            """)
    int deleteUnprocessed(@Param("recId") UUID recId);

    /**
     * Cập nhật số tiền cho các buổi chưa chạy, giữ nguyên ngày chạy.
     *
     * @param recId  ID giao dịch định kỳ cha
     * @param amount số tiền mới
     * @return số buổi đã cập nhật
     */
    @Modifying
    @Query("""
            UPDATE RecTxnSchedule s
            SET s.amount = :amount
            WHERE s.recTxn.id = :recId
              AND (s.status = com.datn.financeapp.module.transaction.enums.RecTxnScheduleStatus.PENDING
                   OR (s.status = com.datn.financeapp.module.transaction.enums.RecTxnScheduleStatus.SKIPPED
                       AND s.runAt > CURRENT_TIMESTAMP))
            """)
    int updateUnprocessedAmount(@Param("recId") UUID recId, @Param("amount") Long amount);
}

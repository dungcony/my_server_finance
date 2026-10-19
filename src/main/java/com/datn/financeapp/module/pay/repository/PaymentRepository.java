package com.datn.financeapp.module.pay.repository;

import com.datn.financeapp.module.pay.entity.Payment;
import com.datn.financeapp.module.pay.enums.PaymentStatus;
import com.datn.financeapp.module.pay.enums.PaymentType;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository thao tác với bảng {@code payments}.
 */
public interface PaymentRepository extends JpaRepository<Payment, UUID>, JpaSpecificationExecutor<Payment> {

    // tìm đơn thanh toán theo mã orderCode của payOS
    Optional<Payment> findByOrderCode(Long orderCode);

    // kiểm tra đơn hàng có thuộc sở hữu của người dùng hay không mà không nạp entity vào context
    boolean existsByOrderCodeAndUserId(Long orderCode, UUID userId);

    // tìm đơn theo mã và người dùng sở hữu
    Optional<Payment> findByOrderCodeAndUserId(Long orderCode, UUID userId);

    // tìm đơn theo người dùng, loại thanh toán và trạng thái
    Optional<Payment> findByUserIdAndTypeAndStatus(UUID userId, PaymentType type, PaymentStatus status);

    // khoá bi quan NOWAIT đơn PENDING để tránh deadlock với luồng webhook hoặc job
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
    @Query("select p from Payment p where p.userId = :userId and p.type = :type and p.status = :status")
    Optional<Payment> findPendingForUpdateNoWait(@Param("userId") UUID userId,
                                                @Param("type") PaymentType type,
                                                @Param("status") PaymentStatus status);

    // khoá bi quan NOWAIT đơn đang mở (PENDING hoặc PARTIALLY_PAID) khi tạo đơn mới
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
    @Query("select p from Payment p where p.userId = :userId and p.type = :type and p.status in :statuses")
    Optional<Payment> findActiveForUpdateNoWait(@Param("userId") UUID userId,
                                               @Param("type") PaymentType type,
                                               @Param("statuses") Collection<PaymentStatus> statuses);

    // khoá bi quan theo orderCode khi xử lý webhook hoặc cập nhật trạng thái
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.orderCode = :orderCode")
    Optional<Payment> findByOrderCodeForUpdate(@Param("orderCode") Long orderCode);

    // tìm danh sách mã đơn quá hạn thanh toán ban đầu (dành cho PaymentExpiryJob)
    @Query("""
            select p.orderCode from Payment p
            where p.status = :status and p.expiresAt < :now
            order by p.expiresAt
            """)
    List<Long> findOverdueOrderCodes(@Param("status") PaymentStatus status,
                                     @Param("now") Instant now,
                                     Pageable pageable);

    // tìm danh sách mã đơn PENDING còn hạn để job quét đồng bộ trạng thái từ payOS
    @Query("""
            select p.orderCode from Payment p
            where p.status = 'PENDING' and p.expiresAt >= :now
            order by p.createdAt asc
            """)
    List<Long> findActivePendingOrderCodes(@Param("now") Instant now, Pageable pageable);

    // tìm danh sách mã đơn PARTIALLY_PAID đã quá hạn bù tiền để đóng đơn và giữ tiền
    @Query("""
            select p.orderCode from Payment p
            where p.status = 'PARTIALLY_PAID' and p.topupDeadline is not null and p.topupDeadline < :now
            order by p.topupDeadline asc
            """)
    List<Long> findOverdueTopupOrderCodes(@Param("now") Instant now, Pageable pageable);

    // tìm danh sách mã đơn PARTIALLY_PAID sắp đến hạn chót cần gửi thông báo nhắc nhở
    @Query("""
            select p.orderCode from Payment p
            where p.status = 'PARTIALLY_PAID'
              and p.topupDeadline is not null
              and p.topupDeadline <= :reminderThreshold
              and p.topupDeadline > :now
              and p.topupRemindedAt is null
            order by p.topupDeadline asc
            """)
    List<Long> findTopupReminderCandidates(@Param("now") Instant now,
                                           @Param("reminderThreshold") Instant reminderThreshold,
                                           Pageable pageable);

    // lấy giá trị tiếp theo từ sequence mã đơn
    @Query(value = "select nextval('payments_order_code_seq')", nativeQuery = true)
    long nextOrderCode();
}

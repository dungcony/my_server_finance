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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID>, JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByOrderCode(Long orderCode);

    boolean existsByOrderCodeAndUserId(Long orderCode, UUID userId);

    Optional<Payment> findByOrderCodeAndUserId(Long orderCode, UUID userId);

    Optional<Payment> findByUserIdAndTypeAndStatus(UUID userId, PaymentType type, PaymentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
    @Query("select p from Payment p where p.userId = :userId and p.type = :type and p.status = :status")
    Optional<Payment> findPendingForUpdateNoWait(@Param("userId") UUID userId,
                                                @Param("type") PaymentType type,
                                                @Param("status") PaymentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.orderCode = :orderCode")
    Optional<Payment> findByOrderCodeForUpdate(@Param("orderCode") Long orderCode);

    @Query("""
            select p.orderCode from Payment p
            where p.status = :status and p.expiresAt < :now
            order by p.expiresAt
            """)
    List<Long> findOverdueOrderCodes(@Param("status") PaymentStatus status,
                                     @Param("now") Instant now,
                                     Pageable pageable);

    @Query(value = "select nextval('payments_order_code_seq')", nativeQuery = true)
    long nextOrderCode();
}

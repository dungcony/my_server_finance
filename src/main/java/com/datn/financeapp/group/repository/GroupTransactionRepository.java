package com.datn.financeapp.group.repository;

import com.datn.financeapp.group.entity.GTransaction;
import com.datn.financeapp.group.enums.TransactionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.datn.financeapp.group.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupTransactionRepository extends JpaRepository<GTransaction, UUID>, JpaSpecificationExecutor<GTransaction> {

    @EntityGraph(attributePaths = {"participants"})
    Optional<GTransaction> findByIdAndGroupIdAndDeletedAtIsNull(UUID id, UUID groupId);

    List<GTransaction> findByIdInAndGroupIdAndDeletedAtIsNullAndStatus(List<UUID> ids, UUID groupId, TransactionStatus status);

    @EntityGraph(attributePaths = {"participants"})
    List<GTransaction> findByGroupIdAndDeletedAtIsNullOrderByOccurredAtDescCreatedAtDesc(UUID groupId);

    @EntityGraph(attributePaths = {"participants"})
    List<GTransaction> findByGroupIdAndStatusAndDeletedAtIsNullOrderByOccurredAtAscCreatedAtAsc(
            UUID groupId, TransactionStatus status);

    long countByGroupIdAndStatusAndDeletedAtIsNull(UUID groupId, TransactionStatus status);

    @Query("""
                SELECT COALESCE(SUM(gt.amount), 0) FROM GTransaction gt
                WHERE gt.groupId = :groupId
                  AND gt.type = :type
                  AND gt.status = 'CONFIRMED'
                  AND gt.deletedAt IS NULL
                  AND gt.occurredAt >= :fromTime
                  AND gt.occurredAt < :toTime
            """)
    Long sumAmountByGroupIdAndTypeAndPeriod(
            @Param("groupId") UUID groupId,
            @Param("type") TransactionType type,
            @Param("fromTime") Instant fromTime,
            @Param("toTime") Instant toTime);
}

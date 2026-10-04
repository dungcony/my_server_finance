package com.datn.financeapp.group.repository;

import com.datn.financeapp.group.entity.GTransaction;
import com.datn.financeapp.group.enums.GTransactionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.datn.financeapp.group.enums.GTransactionType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface GroupTransactionRepository extends JpaRepository<GTransaction, UUID>, JpaSpecificationExecutor<GTransaction> {

    @EntityGraph(attributePaths = {"participants"})
    Optional<GTransaction> findByIdAndGroupIdAndDeletedAtIsNull(UUID id, UUID groupId);

    List<GTransaction> findByIdInAndGroupIdAndDeletedAtIsNullAndStatus(List<UUID> ids, UUID groupId, GTransactionStatus status);

    @EntityGraph(attributePaths = {"participants"})
    List<GTransaction> findByGroupIdAndDeletedAtIsNullOrderByOccurredAtDescCreatedAtDesc(UUID groupId);

    @EntityGraph(attributePaths = {"participants"})
    java.util.stream.Stream<GTransaction> streamByGroupIdAndStatusAndDeletedAtIsNullOrderByOccurredAtAscCreatedAtAsc(
            UUID groupId,
            GTransactionStatus status
    );

    long countByGroupIdAndStatusAndDeletedAtIsNull(UUID groupId, GTransactionStatus status);

    @Query("""
                SELECT COALESCE(SUM(gt.amount), 0)
                FROM GTransaction gt
                WHERE gt.groupId = :groupId
                  AND gt.type = :type
                  AND gt.status = GTransactionStatus.CONFIRMED
                  AND gt.deletedAt IS NULL
                  AND gt.occurredAt >= :fromTime
                  AND gt.occurredAt < :toTime
            """)
    Long sumAmountByGroupIdAndTypeAndPeriod(
            UUID groupId,
            GTransactionType type,
            Instant fromTime,
            Instant toTime);

    @Query("""
                SELECT COALESCE(SUM(gt.amount), 0)
                FROM GTransaction gt
                WHERE gt.groupId = :groupId
                  AND gt.type = :type
                  AND gt.status = GTransactionStatus.CONFIRMED
                  AND gt.deletedAt IS NULL
            """)
    Long sumAmountByGroupIdAndType(
            UUID groupId,
            GTransactionType type);
}

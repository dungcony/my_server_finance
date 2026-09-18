package com.datn.financeapp.group.repository;

import com.datn.financeapp.group.entity.GroupTransaction;
import com.datn.financeapp.group.enums.GroupTransactionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupTransactionRepository extends JpaRepository<GroupTransaction, UUID> {

    Optional<GroupTransaction> findByIdAndGroupIdAndDeletedAtIsNull(UUID id, UUID groupId);

    List<GroupTransaction> findByIdInAndGroupIdAndDeletedAtIsNull(List<UUID> ids, UUID groupId);

    List<GroupTransaction> findByGroupIdAndDeletedAtIsNullOrderByOccurredAtDescCreatedAtDesc(UUID groupId);

    List<GroupTransaction> findByGroupIdAndStatusAndDeletedAtIsNullOrderByOccurredAtAscCreatedAtAsc(
            UUID groupId, GroupTransactionStatus status);

    long countByGroupIdAndDeletedAtIsNull(UUID groupId);

    long countByGroupIdAndStatusAndDeletedAtIsNull(UUID groupId, GroupTransactionStatus status);

    @Query(
        value = """
            SELECT gt.* FROM group_transactions gt
            WHERE gt.group_id = :groupId
              AND gt.deleted_at IS NULL
              AND (CAST(:moneySource AS text) IS NULL OR gt.money_source = CAST(:moneySource AS text))
              AND (CAST(:type AS text) IS NULL OR gt.type = CAST(:type AS text))
              AND (CAST(:status AS text) IS NULL OR gt.status = CAST(:status AS text))
              AND (CAST(:userId AS uuid) IS NULL OR gt.user_id = CAST(:userId AS uuid))
              AND (CAST(:fromOccurredAt AS timestamptz) IS NULL OR gt.occurred_at >= CAST(:fromOccurredAt AS timestamptz))
              AND (CAST(:toOccurredAt AS timestamptz) IS NULL OR gt.occurred_at <= CAST(:toOccurredAt AS timestamptz))
            ORDER BY gt.occurred_at DESC, gt.created_at DESC
            LIMIT :limit OFFSET :offset
        """,
        nativeQuery = true)
    List<GroupTransaction> findFiltered(
            @Param("groupId") UUID groupId,
            @Param("moneySource") String moneySource,
            @Param("type") String type,
            @Param("status") String status,
            @Param("userId") UUID userId,
            @Param("fromOccurredAt") Instant fromOccurredAt,
            @Param("toOccurredAt") Instant toOccurredAt,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Query(
        value = """
            SELECT COUNT(*) FROM group_transactions gt
            WHERE gt.group_id = :groupId
              AND gt.deleted_at IS NULL
              AND (CAST(:moneySource AS text) IS NULL OR gt.money_source = CAST(:moneySource AS text))
              AND (CAST(:type AS text) IS NULL OR gt.type = CAST(:type AS text))
              AND (CAST(:status AS text) IS NULL OR gt.status = CAST(:status AS text))
              AND (CAST(:userId AS uuid) IS NULL OR gt.user_id = CAST(:userId AS uuid))
              AND (CAST(:fromOccurredAt AS timestamptz) IS NULL OR gt.occurred_at >= CAST(:fromOccurredAt AS timestamptz))
              AND (CAST(:toOccurredAt AS timestamptz) IS NULL OR gt.occurred_at <= CAST(:toOccurredAt AS timestamptz))
        """,
        nativeQuery = true)
    long countFiltered(
            @Param("groupId") UUID groupId,
            @Param("moneySource") String moneySource,
            @Param("type") String type,
            @Param("status") String status,
            @Param("userId") UUID userId,
            @Param("fromOccurredAt") Instant fromOccurredAt,
            @Param("toOccurredAt") Instant toOccurredAt);

    @Query("SELECT COALESCE(SUM(gt.amount), 0) FROM GroupTransaction gt WHERE gt.groupId = :groupId AND gt.type = 'EXPENSE' AND gt.status = 'CONFIRMED' AND gt.deletedAt IS NULL")
    Long sumExpenseByGroupId(@Param("groupId") UUID groupId);

    @Query("SELECT COALESCE(SUM(gt.amount), 0) FROM GroupTransaction gt WHERE gt.groupId = :groupId AND gt.type = 'CONTRIBUTION' AND gt.status = 'CONFIRMED' AND gt.deletedAt IS NULL")
    Long sumContributionByGroupId(@Param("groupId") UUID groupId);

    @Query("""
        SELECT COALESCE(SUM(gt.amount), 0) FROM GroupTransaction gt
        WHERE gt.groupId = :groupId
          AND gt.type = :type
          AND gt.status = 'CONFIRMED'
          AND gt.deletedAt IS NULL
          AND gt.occurredAt >= :fromTime
          AND gt.occurredAt < :toTime
    """)
    Long sumAmountByGroupIdAndTypeAndPeriod(
            @Param("groupId") UUID groupId,
            @Param("type") com.datn.financeapp.group.enums.GroupTransactionType type,
            @Param("fromTime") Instant fromTime,
            @Param("toTime") Instant toTime);
}

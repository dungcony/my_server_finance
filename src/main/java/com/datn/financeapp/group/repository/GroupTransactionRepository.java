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

    interface MemberBalanceProjection {
        UUID getUserId();

        long getPaidOutOfPocket();

        long getContribution();

        long getRefund();

        long getShare();
    }

    @Query(value = """
            SELECT
                user_id,
                COALESCE(SUM(paid_out_of_pocket), 0) AS paid_out_of_pocket,
                COALESCE(SUM(contribution), 0)       AS contribution,
                COALESCE(SUM(refund), 0)             AS refund,
                COALESCE(SUM(share), 0)              AS share
            FROM (
                SELECT transactor_id AS user_id, amount AS paid_out_of_pocket, 0 AS contribution, 0 AS refund, 0 AS share
                FROM group_transactions
                WHERE group_id = :groupId 
                  AND status = 'CONFIRMED' 
                  AND deleted_at IS NULL
                  AND type = 'EXPENSE' 
                  AND money_source = 'PERSONAL'
            
                UNION ALL
            
                SELECT transactor_id AS user_id, 0, amount, 0, 0
                FROM group_transactions
                WHERE group_id = :groupId 
                  AND status = 'CONFIRMED' 
                  AND deleted_at IS NULL
                  AND type = 'CONTRIBUTION'
            
                UNION ALL
            
                SELECT transactor_id AS user_id, 0, 0, amount, 0
                FROM group_transactions
                WHERE group_id = :groupId 
                  AND status = 'CONFIRMED' 
                  AND deleted_at IS NULL
                  AND type = 'REFUND'
            
                UNION ALL
            
                SELECT 
                    p.user_id, 
                    0, 
                    0, 
                    0, 
                    CASE 
                        WHEN gt.type = 'ADJUSTMENT_UP' THEN -p.share_amount 
                        ELSE p.share_amount 
                    END AS share
                FROM group_transaction_participants p
                JOIN group_transactions gt ON gt.id = p.group_transaction_id
                WHERE gt.group_id = :groupId 
                  AND gt.status = 'CONFIRMED' 
                  AND gt.deleted_at IS NULL
            ) combined
            GROUP BY user_id
            """, nativeQuery = true)
    List<MemberBalanceProjection> aggregateMemberBalancesByGroupId(UUID groupId);
}

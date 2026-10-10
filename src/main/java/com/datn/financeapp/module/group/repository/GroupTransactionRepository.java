package com.datn.financeapp.module.group.repository;

import com.datn.financeapp.module.group.entity.GTransaction;
import com.datn.financeapp.module.group.enums.GTransactionStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.datn.financeapp.module.group.enums.GTransactionType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface GroupTransactionRepository extends
        JpaRepository<GTransaction, UUID>,
        JpaSpecificationExecutor<GTransaction>,
        GroupTransactionRepositoryCustom {

    @EntityGraph(attributePaths = {"participants"})
    Optional<GTransaction> findByIdAndGroupIdAndDeletedAtIsNull(UUID id, UUID groupId);

    List<GTransaction> findByIdInAndGroupIdAndDeletedAtIsNullAndStatus(List<UUID> ids, UUID groupId, GTransactionStatus status);

    long countByGroupIdAndStatusAndDeletedAtIsNull(UUID groupId, GTransactionStatus status);

    long countByGroupIdAndDeletedAtIsNull(UUID groupId);

    long countByGroupIdAndDeletedAtIsNullAndStatusNotIn(UUID groupId, Collection<GTransactionStatus> statuses);

    long countByGroupIdAndStatusInAndDeletedAtIsNull(UUID groupId, Collection<GTransactionStatus> statuses);

    // giao dịch ở một trạng thái mà thành viên là người tạo hoặc người thực hiện (dùng khi thành viên rời nhóm)
    @Query("""
                SELECT gt
                FROM GTransaction gt
                WHERE gt.groupId = :groupId
                  AND gt.status = :status
                  AND gt.deletedAt IS NULL
                  AND (gt.createdBy = :memberId OR gt.transactorId = :memberId)
            """)
    List<GTransaction> findByStatusOfMember(UUID groupId, GTransactionStatus status, UUID memberId);

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


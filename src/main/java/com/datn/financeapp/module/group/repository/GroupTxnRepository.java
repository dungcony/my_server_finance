package com.datn.financeapp.module.group.repository;

import com.datn.financeapp.module.group.entity.GroupTxn;
import com.datn.financeapp.module.group.enums.GroupTxnStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.datn.financeapp.module.group.enums.GroupTxnType;
import org.springframework.data.jpa.repository.*;

public interface GroupTxnRepository extends
        JpaRepository<GroupTxn, UUID>,
        JpaSpecificationExecutor<GroupTxn>,
        GroupTxnRepositoryCustom {

    @EntityGraph(attributePaths = {"participants"})
    Optional<GroupTxn> findByIdAndGroupIdAndDeletedAtIsNull(UUID id, UUID groupId);

    @EntityGraph(attributePaths = {"participants"})
    Optional<GroupTxn> findByIdAndStatusAndDeletedAtIsNull(UUID id, GroupTxnStatus status);

    @EntityGraph(attributePaths = {"participants"})
    List<GroupTxn> findByIdInAndStatusAndDeletedAtIsNull(List<UUID> ids, GroupTxnStatus status);

    @EntityGraph(attributePaths = {"participants"})
    List<GroupTxn> findByIdInAndGroupIdAndDeletedAtIsNullAndStatus(List<UUID> ids, UUID groupId, GroupTxnStatus status);

    long countByGroupIdAndStatusAndDeletedAtIsNull(UUID groupId, GroupTxnStatus status);

    long countByGroupIdAndDeletedAtIsNullAndStatusNotIn(UUID groupId, Collection<GroupTxnStatus> statuses);

    long countByGroupIdAndStatusInAndDeletedAtIsNull(UUID groupId, Collection<GroupTxnStatus> statuses);

    @Query("""
                SELECT COALESCE(SUM(gt.amount), 0)
                FROM GroupTxn gt
                WHERE gt.groupId = :groupId
                  AND gt.type = :type
                  AND gt.status = GroupTxnStatus.CONFIRMED
                  AND gt.deletedAt IS NULL
                  AND gt.occurredAt >= :fromTime
                  AND gt.occurredAt < :toTime
            """)
    Long sumAmountByGroupIdAndTypeAndPeriod(
            UUID groupId,
            GroupTxnType type,
            Instant fromTime,
            Instant toTime);

    @Query("""
                SELECT COALESCE(SUM(gt.amount), 0)
                FROM GroupTxn gt
                WHERE gt.groupId = :groupId
                  AND gt.type = :type
                  AND gt.status = GroupTxnStatus.CONFIRMED
                  AND gt.deletedAt IS NULL
            """)
    Long sumAmountByGroupIdAndType(
            UUID groupId,
            GroupTxnType type);
}

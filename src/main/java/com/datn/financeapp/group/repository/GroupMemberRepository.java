package com.datn.financeapp.group.repository;

import com.datn.financeapp.group.entity.GroupMember;
import com.datn.financeapp.group.enums.GroupRole;
import com.datn.financeapp.group.enums.MemberStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupMemberRepository extends JpaRepository<GroupMember, UUID> {

    Optional<GroupMember> findByGroupIdAndUserIdAndStatusIn(UUID groupId, UUID userId, Collection<MemberStatus> statuses);

    default Optional<GroupMember> findCurrentMember(UUID groupId, UUID userId) {
        return findByGroupIdAndUserIdAndStatusIn(groupId, userId, List.of(MemberStatus.ACTIVE, MemberStatus.PENDING));
    }

    Optional<GroupMember> findByGroupIdAndUserIdAndStatus(UUID groupId, UUID userId, MemberStatus status);

    Optional<GroupMember> findByGroupIdAndRoleAndStatus(UUID groupId, GroupRole role, MemberStatus status);

    List<GroupMember> findByGroupIdAndStatus(UUID groupId, MemberStatus status);

    List<GroupMember> findByGroupIdOrderByJoinedAtDesc(UUID groupId);

    long countByGroupIdAndStatus(UUID groupId, MemberStatus status);

    boolean existsByGroupIdAndUserIdAndRoleAndStatus(UUID groupId, UUID userId, GroupRole role, MemberStatus status);

    boolean existsByGroupIdAndUserIdAndStatus(UUID groupId, UUID userId, MemberStatus status);

    /**
     * Thành viên có mặt tại thời điểm giao dịch (theo pipeline.md mục 1).
     * Dùng khi giao dịch không có participants -> chia cho tất cả thành viên có mặt lúc đó.
     */
    @Query(value = """
        SELECT DISTINCT user_id
        FROM group_members
        WHERE group_id = :groupId
          AND status IN ('ACTIVE', 'LEFT', 'REMOVED')
          AND joined_at <= :occurredAt
          AND (left_at IS NULL OR left_at > :occurredAt)
        ORDER BY user_id
    """, nativeQuery = true)
    List<UUID> findMemberUserIdsAtOccurredAt(@Param("groupId") UUID groupId, @Param("occurredAt") Instant occurredAt);

    /**
     * Backward compatibility theo ngày giao dịch (nếu cần).
     */
    @Query(value = """
        SELECT DISTINCT user_id
        FROM group_members
        WHERE group_id = :groupId
          AND status IN ('ACTIVE', 'LEFT', 'REMOVED')
          AND (joined_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date <= :txnDate
          AND (left_at IS NULL OR (left_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date > :txnDate)
        ORDER BY user_id
    """, nativeQuery = true)
    List<UUID> findMemberUserIdsOnDate(@Param("groupId") UUID groupId, @Param("txnDate") LocalDate txnDate);
}

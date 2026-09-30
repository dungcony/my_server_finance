package com.datn.financeapp.group.repository;

import com.datn.financeapp.group.entity.Member;
import com.datn.financeapp.group.enums.MemberRole;
import com.datn.financeapp.group.enums.MemberStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, UUID> {
    Optional<Member> findByGroupIdAndUserId(UUID groupId, UUID userId);

    Optional<Member> findByGroupIdAndUserIdAndStatus(UUID groupId, UUID userId, MemberStatus status);

    Optional<Member> findByGroupIdAndRoleAndStatus(UUID groupId, MemberRole role, MemberStatus status);

    Optional<Member> findByGroupIdAndUserIdAndStatusIn(UUID groupId, UUID userId, List<MemberStatus> status);

    List<Member> findByGroupIdAndStatusNotOrderByJoinedAtDesc(UUID groupId, MemberStatus status);

    List<Member> findByGroupIdAndStatusOrderByJoinedAtDesc(UUID groupId, MemberStatus status);

    long countByGroupIdAndStatus(UUID groupId, MemberStatus status);

    boolean existsByGroupIdAndUserIdAndRoleAndStatus(UUID groupId, UUID userId, MemberRole role,
            MemberStatus status);

    boolean existsByGroupIdAndUserIdAndStatus(UUID groupId, UUID userId, MemberStatus status);

    // Lấy các thành viên đang ACTIVE theo danh sách userId
    List<Member> findByGroupIdAndUserIdInAndStatus(UUID groupId, Collection<UUID> userIds, MemberStatus status);

    /**
     * Thành viên có mặt tại thời điểm giao dịch (theo pipeline.md mục 1).
     * Dùng khi giao dịch không có participants -> chia cho tất cả thành viên có mặt
     * lúc đó.
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
    List<UUID> findMemberUserIdsAtOccurredAt(@Param("groupId") UUID groupId,
            @Param("occurredAt") Instant occurredAt);

    @Modifying(clearAutomatically = true)
    @Query("""
                UPDATE Member gm
                SET gm.status = :newStatus,
                    gm.joinedAt = :joinedAt
                WHERE gm.groupId = :groupId
                  AND gm.userId = :memberId
                  AND gm.status = :oldStatus
            """)
    int updateStatusAndJoinedAt(
            @Param("groupId") UUID groupId,
            @Param("memberId") UUID memberId,
            @Param("oldStatus") MemberStatus oldStatus,
            @Param("newStatus") MemberStatus newStatus,
            @Param("joinedAt") Instant joinedAt);

    @Modifying(clearAutomatically = true)
    @Query("""
                UPDATE Member gm
                SET gm.status = MemberStatus.ACTIVE,
                    gm.joinedAt = :joinedAt
                WHERE gm.groupId = :groupId
                  AND gm.status = MemberStatus.PENDING
            """)
    int approvePending(
            @Param("groupId") UUID groupId,
            @Param("joinedAt") Instant joinedAt);

    @Query("""
            select case
                when count(mem) = :#{#memberIds.size()}
                then true
                else false
            end
            from Member mem
            where mem.groupId = :groupId
            and mem.userId in (:memberIds)
            """)
    boolean allMemberInGroup(
            @Param("groupId") UUID groupId,
            @Param("memberIds") Collection<UUID> memberIds);
}

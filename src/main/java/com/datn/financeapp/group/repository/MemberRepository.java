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

public interface MemberRepository extends JpaRepository<Member, UUID> {
    Optional<Member> findByGroupIdAndUserId(UUID groupId, UUID userId);

    Optional<Member> findByGroupIdAndUserIdAndStatusIn(UUID groupId, UUID userId, List<MemberStatus> statuses);

    Optional<Member> findByGroupIdAndUserIdAndStatus(UUID groupId, UUID userId, MemberStatus status);

    Optional<Member> findByGroupIdAndRoleAndStatus(UUID groupId, MemberRole role, MemberStatus status);

    List<Member> findByGroupIdAndStatusNotOrderByJoinedAtDesc(UUID groupId, MemberStatus status);

    List<Member> findByGroupIdAndStatusOrderByJoinedAtDesc(UUID groupId, MemberStatus status);

    List<Member> findAllByGroupIdAndStatus(UUID groupId, MemberStatus status);

    List<Member> findAllByGroupIdAndStatusIn(UUID groupId, List<MemberStatus> status);

    long countByGroupIdAndStatus(UUID groupId, MemberStatus status);

    // Lấy các thành viên theo danh sách userId có trạng thái nằm trong danh sách
    List<Member> findByGroupIdAndUserIdInAndStatusIn(UUID groupId, Collection<UUID> userIds, Collection<MemberStatus> statuses);

    boolean existsByGroupIdAndUserIdAndStatus(UUID groupId, UUID memberId, MemberStatus memberStatus);

    // MemberRepository
    @Modifying
    @Query("""
                DELETE FROM Member m
                 WHERE m.groupId = :groupId
                   AND m.userId = :memberId
                   AND m.status = MemberStatus.PENDING
            """)
    int deletePending(UUID groupId, UUID memberId);

    // Xóa toàn bộ thành viên đang PENDING của nhóm, trả về số dòng đã xóa
    @Modifying
    @Query("""
                DELETE FROM Member m
                 WHERE m.groupId = :groupId
                   AND m.status = MemberStatus.PENDING
            """)
    int deleteAllPending(UUID groupId);

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
    List<UUID> findMemberUserIdsAtOccurredAt(UUID groupId,
                                             Instant occurredAt);

    @Modifying
    @Query("""
                UPDATE Member gm
                SET gm.status = :newStatus,
                    gm.joinedAt = :joinedAt
                WHERE gm.groupId = :groupId
                  AND gm.userId = :memberId
                  AND gm.status = :oldStatus
            """)
    int updateStatusAndJoinedAt(
            UUID groupId,
            UUID memberId,
            MemberStatus oldStatus,
            MemberStatus newStatus,
            Instant joinedAt);

    @Modifying
    @Query("""
                UPDATE Member gm
                SET gm.status = MemberStatus.ACTIVE,
                    gm.joinedAt = :joinedAt
                WHERE gm.groupId = :groupId
                  AND gm.status = MemberStatus.PENDING
            """)
    int approvePending(
            UUID groupId,
            Instant joinedAt);

    @Query("""
            select case
                when count(mem) = :#{#memberIds.size()}
                then true
                else false
            end
            from Member mem
            where mem.groupId = :groupId
            and mem.userId in (:memberIds)
            and mem.status = 'ACTIVE'
            """)
    boolean allMemberInGroup(
            UUID groupId,
            Collection<UUID> memberIds);

    @Modifying
    @Query("""
                UPDATE Member m
                   SET m.role = CASE
                               WHEN m.userId = :newOwnerId
                               THEN com.datn.financeapp.group.enums.MemberRole.OWNER
                               ELSE com.datn.financeapp.group.enums.MemberRole.MEMBER
                   END
                 WHERE m.groupId = :groupId
                   AND m.status = MemberStatus.ACTIVE
                   AND m.userId IN (:operatorId, :newOwnerId)
                   AND EXISTS (
                               SELECT 1
                               FROM Member o
                               WHERE o.groupId = :groupId
                                AND o.userId = :operatorId
                                AND o.role = MemberRole.OWNER
                                AND o.status = MemberStatus.ACTIVE
                              )
            """)
    int swapOwner(UUID groupId, UUID operatorId, UUID newOwnerId);
}

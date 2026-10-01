package com.datn.financeapp.group.repository;

import com.datn.financeapp.group.dto.response.group.GroupSummaryRes;
import com.datn.financeapp.group.entity.Group;
import com.datn.financeapp.group.enums.GroupStatus;
import com.datn.financeapp.group.helper.MemberAuthInfo;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupRepository extends JpaRepository<Group, UUID> {

    Optional<Group> findByInviteCodeAndStatusNot(String inviteCode, GroupStatus status);

    Optional<Group> findByIdAndStatus(UUID id, GroupStatus status);

    @Query("""
                SELECT new com.datn.financeapp.group.helper.MemberAuthInfo(
                    g.id,
                    gm.userId,
                    g.status,
                    g.isSettlementEnabled,
                    gm.status,
                    gm.role,
                    f.keepperId
                )
                FROM Group g
                LEFT JOIN Member gm ON gm.groupId = g.id AND gm.userId = :userId
                LEFT JOIN Fund f ON f.groupId = g.id
                WHERE g.id = :groupId and gm.status = MemberStatus.ACTIVE
            """)
    Optional<MemberAuthInfo> findAuthInfo(
            @Param("groupId") UUID groupId,
            @Param("userId") UUID userId);

    @Query("""
                SELECT new com.datn.financeapp.group.helper.MemberAuthInfo(
                    g.id,
                    gm.userId,
                    g.status,
                    g.isSettlementEnabled,
                    gm.status,
                    gm.role,
                    f.keepperId
                )
                FROM Group g
                LEFT JOIN Member gm ON gm.groupId = g.id AND gm.userId = :userId
                LEFT JOIN Fund f ON f.groupId = g.id
                WHERE g.inviteCode = :inviteCode and gm.status = MemberStatus.ACTIVE
            """)
    Optional<MemberAuthInfo> findAuthInfo(
            String inviteCode,
            @Param("userId") UUID userId);

    @Query("""
            SELECT new com.datn.financeapp.group.dto.response.group.GroupSummaryRes(
                g.id,
                g.name,
                gm.role,
                g.status,
                g.inviteCode,
                (SELECT COUNT(m2) FROM Member m2
                  WHERE m2.groupId = g.id AND m2.status = MemberStatus.ACTIVE),
                f.currentBalance,
                g.target,
                g.createdAt
            )
            FROM Group g
            JOIN Member gm ON gm.groupId = g.id
                            AND gm.userId = :userId
                            AND gm.status = MemberStatus.ACTIVE
            LEFT JOIN Fund f ON f.groupId = g.id
            WHERE g.status <> GroupStatus.DELETED
            ORDER BY g.createdAt DESC
            """)
    List<GroupSummaryRes> findSummariesByUserId(UUID userId);

    @Query("""
            SELECT g
            FROM Group g
            LEFT JOIN FETCH g.fund
            WHERE g.id = :id and g.status <> GroupStatus.DELETED
            """)
    Optional<Group> findNotDeletedWithFundById(@Param("id") UUID id);

    @Query("""
            SELECT g
            FROM Group g
            LEFT JOIN FETCH g.fund
            WHERE g.id = :id and g.status = GroupStatus.ACTIVE
            """)
    Optional<Group> findActivatedWithFundById(@Param("id") UUID id);

    @Query("""
            SELECT g
            FROM Group g
            LEFT JOIN FETCH g.fund
            WHERE g.id = :id and g.status = GroupStatus.ARCHIVED
            """)
    Optional<Group> findArchivedWithFundById(@Param("id") UUID id);

}

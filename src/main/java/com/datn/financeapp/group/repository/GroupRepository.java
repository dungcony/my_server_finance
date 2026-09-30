package com.datn.financeapp.group.repository;

import com.datn.financeapp.group.entity.Group;
import com.datn.financeapp.group.enums.GroupStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.datn.financeapp.group.helper.MemberAuthInfo;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
                    f.heldByUserId
                )
                FROM Group g
                LEFT JOIN Member gm ON gm.groupId = g.id AND gm.userId = :userId
                LEFT JOIN Fund f ON f.groupId = g.id
                WHERE g.id = :groupId
            """)
    Optional<MemberAuthInfo> findAuthInfo(
            @Param("groupId") UUID groupId,
            @Param("userId") UUID userId
    );

    @EntityGraph(attributePaths = {"fund"})
    @Query("""
                SELECT g FROM Group g
                JOIN Member gm ON gm.groupId = g.id
                WHERE gm.userId = :userId AND gm.status = 'ACTIVE' AND g.status <> 'DELETED'
                ORDER BY g.createdAt DESC
            """)
    List<Group> findAllActiveByUserId(@Param("userId") UUID userId);

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

package com.datn.financeapp.group.repository;

import com.datn.financeapp.group.entity.Group;
import com.datn.financeapp.group.enums.GroupStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupRepository extends JpaRepository<Group, UUID> {

    Optional<Group> findByInviteCodeAndStatusNot(String inviteCode, GroupStatus status);

    @Query("""
        SELECT g FROM Group g
        JOIN GroupMember gm ON gm.groupId = g.id
        WHERE gm.userId = :userId AND gm.status = 'ACTIVE' AND g.status <> 'DELETED'
        ORDER BY g.createdAt DESC
    """)
    List<Group> findAllActiveByUserId(@Param("userId") UUID userId);

    @Query("""
        SELECT g FROM Group g
        JOIN GroupMember gm ON gm.groupId = g.id
        WHERE g.id = :groupId AND gm.userId = :userId AND gm.status = 'ACTIVE' AND g.status <> 'DELETED'
    """)
    Optional<Group> findActiveByIdAndUserId(@Param("groupId") UUID groupId, @Param("userId") UUID userId);
}

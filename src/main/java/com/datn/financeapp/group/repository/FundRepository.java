package com.datn.financeapp.group.repository;

import com.datn.financeapp.group.entity.Fund;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface FundRepository extends JpaRepository<Fund, UUID> {

    Optional<Fund> findByGroupId(UUID groupId);

    Optional<Fund> findFirstByGroupId(UUID groupId);

    @Modifying
    @Query("""
            UPDATE Fund gf
            SET gf.currentBalance = gf.currentBalance + :delta
            WHERE gf.id = :id
            """)
    int adjustBalance(UUID id, Long delta);

    @Modifying
    @Query("UPDATE Fund gf SET gf.currentBalance = gf.currentBalance + :delta WHERE gf.groupId = :groupId")
    int adjustBalanceByGroupId(UUID groupId, Long delta);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select f
            from Fund f
            where f.groupId = :groupId
            """)
    Optional<Fund> findByGroupIdForUpdate(UUID groupId);
}

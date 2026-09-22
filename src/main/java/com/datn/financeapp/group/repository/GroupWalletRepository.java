package com.datn.financeapp.group.repository;

import com.datn.financeapp.group.entity.GroupWallet;
import com.datn.financeapp.group.enums.GroupWalletStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupWalletRepository extends JpaRepository<GroupWallet, UUID> {

    Optional<GroupWallet> findByGroupId(UUID groupId);

    Optional<GroupWallet> findByGroupIdAndStatus(UUID groupId, GroupWalletStatus status);

    Optional<GroupWallet> findFirstByGroupId(UUID groupId);

    Optional<GroupWallet> findFirstByGroupIdAndStatus(UUID groupId, GroupWalletStatus status);

    @Modifying
    @Query("UPDATE GroupWallet gw SET gw.currentBalance = gw.currentBalance + :delta WHERE gw.id = :id")
    int adjustBalance(@Param("id") UUID id, @Param("delta") Long delta);

    @Query(value = "SELECT * FROM group_wallets WHERE group_id = :groupId AND status = 'ACTIVE' FOR UPDATE", nativeQuery = true)
    Optional<GroupWallet> findByGroupIdForUpdate(@Param("groupId") UUID groupId);
}

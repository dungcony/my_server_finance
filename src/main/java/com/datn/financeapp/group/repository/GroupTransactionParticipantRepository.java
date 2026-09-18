package com.datn.financeapp.group.repository;

import com.datn.financeapp.group.entity.GroupTransactionParticipant;
import com.datn.financeapp.group.entity.GroupTransactionParticipantId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupTransactionParticipantRepository
        extends JpaRepository<GroupTransactionParticipant, GroupTransactionParticipantId> {

    List<GroupTransactionParticipant> findByGroupTransactionId(UUID groupTransactionId);

    List<GroupTransactionParticipant> findByGroupTransactionIdIn(Collection<UUID> groupTransactionIds);

    @Modifying
    @Query("DELETE FROM GroupTransactionParticipant p WHERE p.groupTransactionId = :groupTransactionId")
    void deleteByGroupTransactionId(@Param("groupTransactionId") UUID groupTransactionId);
}

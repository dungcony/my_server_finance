package com.datn.financeapp.group.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "group_transaction_participants")
@IdClass(GroupTransactionParticipant.GroupTransactionParticipantId.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupTransactionParticipant {

    @Id
    @Column(name = "group_transaction_id", nullable = false)
    private UUID groupTransactionId;

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "share_amount")
    private Long shareAmount;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class GroupTransactionParticipantId implements Serializable {
        private UUID groupTransactionId;
        private UUID userId;
    }
}

package com.datn.financeapp.group.entity;

import com.datn.financeapp.group.enums.GroupWalletStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "group_wallets", uniqueConstraints = {
    @UniqueConstraint(name = "uq_group_wallets_group", columnNames = "group_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupWallet {

    @Id
    private UUID id;

    @Column(name = "group_id", nullable = false, unique = true)
    private UUID groupId;

    @Column(name = "held_by_user_id", nullable = false)
    private UUID heldByUserId;

    @Column(name = "current_balance", nullable = false)
    @Builder.Default
    private Long currentBalance = 0L;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private GroupWalletStatus status = GroupWalletStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

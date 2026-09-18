package com.datn.financeapp.group.entity;

import com.datn.financeapp.group.enums.GroupStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "groups")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Group {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "invite_code", nullable = false, length = 32)
    private String inviteCode;

    @Column(name = "invite_code_expires_at", nullable = false)
    private Instant inviteCodeExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private GroupStatus status = GroupStatus.ACTIVE;

    @Column(name = "target")
    private Long target;

    @Column(name = "is_settlement_enabled", nullable = false)
    @Builder.Default
    private Boolean isSettlementEnabled = true;

    @Column(name = "is_join_without_confirm", nullable = false)
    @Builder.Default
    private Boolean isJoinWithoutConfirm = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

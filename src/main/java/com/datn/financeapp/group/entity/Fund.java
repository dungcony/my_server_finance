package com.datn.financeapp.group.entity;

import jakarta.annotation.Generated;
import com.datn.financeapp.common.abstracts.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "group_funds", uniqueConstraints = {
        @UniqueConstraint(name = "uq_group_funds_group", columnNames = "group_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Fund extends AssignedIdEntity {

    @Id()
    private UUID id;

    @Column(name = "group_id", nullable = false, unique = true)
    private UUID groupId;

    // liên kết phía sở hữu khóa ngoại cho Hibernate, chặn hoàn toàn setter
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Group group;

    @Column(name = "keepper_id", nullable = false)
    private UUID keepperId;

    @Column(name = "current_balance", nullable = false)
    @Builder.Default
    private Long currentBalance = 0L;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

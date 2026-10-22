package com.datn.financeapp.module.user.entity;

import com.datn.financeapp.common.abstracts.AssignedIdEntity;
import com.datn.financeapp.module.user.enums.PermissionName;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "permissions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Permission extends AssignedIdEntity {

    @Id
    private UUID id;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false, unique = true, length = 100)
    private PermissionName name;

    @Column(name = "description")
    private String description;
}

package com.datn.financeapp.user.entity;

import com.datn.financeapp.user.enums.RoleName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "roles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Role {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false, unique = true, length = 50)
    private RoleName name;

    @Column(name = "level", nullable = false)
    private int level;

    @Column(name = "description")
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Builder.Default
    @OneToMany(mappedBy = "role", fetch = FetchType.LAZY)
    private Set<RolePermission> rolePermissions = new HashSet<>();
}

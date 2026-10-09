package com.datn.financeapp.module.category.entity;

import com.datn.financeapp.common.abstracts.AssignedIdEntity;
import com.datn.financeapp.module.category.enums.CateType;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity ánh xạ với bảng {@code categories}, đại diện cho danh mục thu/chi tài chính trong hệ thống.
 * Quản lý thông tin danh mục bao gồm cây danh mục 2 cấp (danh mục cha/con), danh mục hệ thống hoặc cá nhân.
 */
@Entity
@Table(name = "categories")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category extends AssignedIdEntity {

    @Id
    private UUID id;

    @Size(max = 50)
    @NotNull
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private CateType type;               // EXPENSE | INCOME

    @Size(max = 50)
    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "description")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "icon_id", nullable = false)
    private Icon icon;

    @NotNull
    @Size(min = 7, max = 7)
    @Column(name = "color", nullable = false, length = 7)   // #RRGGBB
    private String color;

    @NotNull
    @Column(name = "level", nullable = false)
    private Integer level = 0;

    @NotNull
    @Column(name = "is_leaf", nullable = false)
    private Boolean isLeaf = true;

    @NotNull
    @Size(max = 255)
    @Column(name = "path", nullable = false)
    private String path;

    @NotNull
    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;
}
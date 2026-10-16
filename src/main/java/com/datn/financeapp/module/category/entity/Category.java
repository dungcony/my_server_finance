package com.datn.financeapp.module.category.entity;

import com.datn.financeapp.common.abstracts.AssignedIdEntity;
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
import org.jspecify.annotations.NonNull;

/**
 * Entity ánh xạ với bảng {@code categories}, đại diện cho danh mục thu/chi tài chính trong hệ thống.
 * Danh mục là cây nhiều cấp do admin quản lý: {@code level} là độ sâu (gốc = 0), {@code path} là chuỗi
 * {@code code} từ gốc tới nó nối bằng {@code /}, {@code isLeaf} cho biết còn con hay không
 * (giao dịch chỉ gắn vào danh mục lá).
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
    @NonNull
    @NotNull
    private UUID id;

    @Size(max = 50)
    @NotNull
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Size(max = 50)
    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "description")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @NotNull
    @Column(name = "icon_id")
    private UUID iconId;

    @NotNull
    @Size(min = 7, max = 7)
    @Column(name = "color", nullable = false, length = 7)   // #RRGGBB
    private String color;

    @NotNull
    @Column(name = "level", nullable = false)
    @Builder.Default
    private Integer level = 0;

    @NotNull
    @Column(name = "is_leaf", nullable = false)
    @Builder.Default
    private Boolean isLeaf = true;

    @NotNull
    @Size(max = 255)
    @Column(name = "path", nullable = false)
    private String path;
}

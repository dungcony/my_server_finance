package com.datn.financeapp.module.category.entity;

import com.datn.financeapp.common.abstracts.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity cho bảng {@code icons} — kho biểu tượng hệ thống (api/03-DANH-MUC.md mục 8). Người dùng
 * không tải ảnh riêng, chỉ chọn {@code icon_id} từ kho này.
 */
@Entity
@Table(name = "icons")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Icon extends AssignedIdEntity {

    @Id
    private UUID id;

    @NotNull
    @Column(name = "code", nullable = false)
    private String code;

    @NotNull
    @Column(name = "display_name", nullable = false)
    private String displayName;

    @NotNull
    @Column(name = "path_data", nullable = false)
    private String pathData;

    @NotNull
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @NotNull
    @Column(name = "icon_group", nullable = false, length = 30)
    private String iconGroup;

    @Column(name = "search_keywords")
    private String searchKeywords;

    @NotNull
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}

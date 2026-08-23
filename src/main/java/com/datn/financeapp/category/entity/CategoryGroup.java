package com.datn.financeapp.category.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity cho bảng {@code category_groups} — nhóm lớn cố định do hệ thống định nghĩa, phục vụ
 * biểu đồ tròn ở màn Báo cáo (api/03-DANH-MUC.md mục 7). Người dùng không thêm/sửa/xoá.
 */
@Entity
@Table(name = "category_groups")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryGroup {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "icon_id")
    private UUID iconId;

    @Column(name = "color", columnDefinition = "bpchar(7)")
    private String color;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}

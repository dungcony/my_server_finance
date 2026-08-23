package com.datn.financeapp.category.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity cho bảng {@code categories} — cây danh mục TỐI ĐA HAI CẤP (db/migration/V1__nen_tang.sql
 * dòng 178-217). {@code userId == null} nghĩa là danh mục hệ thống, dùng chung cho mọi người.
 * Hai trigger {@code fn_categories_validate}/{@code fn_categories_block_demote} là lớp phòng thủ
 * cuối cùng ở tầng DB — Service vẫn phải kiểm tra trước để trả đúng mã lỗi nghiệp vụ.
 */
@Entity
@Table(name = "categories")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category {

    @Id
    private UUID id;

    /** NULL = danh mục mặc định của hệ thống, dùng chung cho mọi người. */
    @Column(name = "user_id")
    private UUID userId;

    /** NULL = danh mục cấp cha. Có giá trị = danh mục con. */
    @Column(name = "parent_category_id")
    private UUID parentCategoryId;

    @Column(name = "category_group_id")
    private UUID categoryGroupId;

    @Column(name = "name", nullable = false)
    private String name;

    /** CHECK IN ('expense','income'). */
    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "icon_id", nullable = false)
    private UUID iconId;

    // Cột Postgres CHAR(7) -> physical type "bpchar", không phải "char"/"varchar".
    @Column(name = "color", columnDefinition = "bpchar(7)")
    private String color;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

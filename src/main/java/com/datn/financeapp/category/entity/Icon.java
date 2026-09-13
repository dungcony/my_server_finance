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
public class Icon {

    @Id
    private UUID id;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "path_data", nullable = false)
    private String pathData;

    // CHECK IN ('an_uong','di_lai','mua_sam','giai_tri','suc_khoe','hoc_tap','tai_chinh','khac').
    @Column(name = "icon_group", nullable = false)
    private String iconGroup;

    @Column(name = "search_keywords")
    private String searchKeywords;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;
}

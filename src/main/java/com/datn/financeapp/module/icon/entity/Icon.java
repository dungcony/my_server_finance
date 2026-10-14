package com.datn.financeapp.module.icon.entity;

import com.datn.financeapp.common.abstracts.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Entity cho bảng {@code icons} — kho biểu tượng hệ thống.
 * Người dùng không tải ảnh riêng, chỉ chọn biểu tượng từ kho này.
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
}

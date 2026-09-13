package com.datn.financeapp.category.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity bảng {@code wallet_category_settings} (V17) — danh mục bị TẮT ở một ví cụ thể.
 *
 * <p><b>Vắng dòng = đang bật.</b> Bảng chỉ ghi các trường hợp lệch mặc định, nên mọi truy vấn đọc
 * phải {@code LEFT JOIN} rồi {@code COALESCE(is_enabled, TRUE)} — {@code INNER JOIN} sẽ làm biến
 * mất mọi danh mục chưa ai đụng tới. Lý do đầy đủ ở đầu file migration.
 *
 * <p>Tắt danh mục là cài đặt HIỂN THỊ: giao dịch cũ đã gán vào nó không bị ảnh hưởng, báo cáo vẫn
 * tính đủ, chỉ màn chọn danh mục là ẩn đi.
 */
@Entity
@Table(name = "wallet_category_settings")
@IdClass(WalletCategorySetting.WalletCategorySettingId.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletCategorySetting {

    @Id
    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Id
    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class WalletCategorySettingId implements Serializable {
        private UUID walletId;
        private UUID categoryId;
    }
}

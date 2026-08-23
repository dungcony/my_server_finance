package com.datn.financeapp.wallet.entity;

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
 * Entity đầy đủ cho bảng {@code wallets} — thay thế {@code common.wallet.WalletMinimal} tạm
 * thời của Phase 1 (D-26). Dùng cho toàn bộ nghiệp vụ ví ở Phase 2 (CRUD, chuyển tiền, đối
 * chiếu, điều chỉnh số dư) và các phase sau.
 */
@Entity
@Table(name = "wallets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Wallet {

    @Id
    private UUID id;

    /** Ví cá nhân hoặc nhóm — đúng 1 trong 2 có giá trị (ck_wallets_owner). */
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "name", nullable = false)
    private String name;

    /** CHECK IN ('cash','bank','e_wallet','credit_card'). */
    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "initial_balance", nullable = false)
    private Long initialBalance;

    @Column(name = "current_balance", nullable = false)
    private Long currentBalance;

    @Column(name = "include_in_total", nullable = false)
    private Boolean includeInTotal;

    @Column(name = "icon")
    private String icon;

    // Cột Postgres CHAR(7) -> Postgres báo physical type là "bpchar" (không phải
    // "char" hay "varchar"). Phải khai đúng columnDefinition="bpchar(7)" để Hibernate
    // ddl-auto=validate khớp type, nếu không sẽ luôn báo lệch dù length đúng.
    @Column(name = "color", columnDefinition = "bpchar(7)")
    private String color;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

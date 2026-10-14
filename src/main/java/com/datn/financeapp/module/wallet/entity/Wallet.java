package com.datn.financeapp.module.wallet.entity;

import com.datn.financeapp.common.abstracts.AssignedIdEntity;
import com.datn.financeapp.module.wallet.enums.WalletType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.util.UUID;

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
public class Wallet extends AssignedIdEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "type", nullable = false)
    private WalletType type;

    @Column(name = "balance", nullable = false)
    private Long balance; // số dư hiện có

    @Column(name = "icon_id")
    private UUID iconId;

    @Column(name = "color", columnDefinition = "bpchar(7)")
    private String color;

    // ví mặc định nhận các khoản tự ghi từ giao dịch nhóm, mỗi người dùng đúng một ví chưa xoá
    @Builder.Default
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted;

}

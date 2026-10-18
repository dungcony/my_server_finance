package com.datn.financeapp.module.wallet.entity;

import com.datn.financeapp.common.abstracts.AssignedIdEntity;
import com.datn.financeapp.module.wallet.enums.WalletType;
import jakarta.persistence.*;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private WalletType type;

    // số dư hiện có, chỉ ghi lúc tạo ví; sau đó do trigger trg_transactions_wallet_balance và job đối chiếu cập nhật,
    // updatable = false để save(wallet) không ghi đè số dư cũ trong bộ nhớ lên số trigger vừa cộng
    @Column(name = "balance", nullable = false, updatable = false)
    private Long balance;

    @Column(name = "icon_code")
    private String iconCode;

    @Column(name = "color", columnDefinition = "bpchar(7)")
    private String color;

    // ví mặc định nhận các khoản tự ghi từ giao dịch nhóm, mỗi người dùng đúng một ví chưa xoá
    @Builder.Default
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;


}

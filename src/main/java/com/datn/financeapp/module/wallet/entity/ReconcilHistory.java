package com.datn.financeapp.module.wallet.entity;

import com.datn.financeapp.common.abstracts.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "reconcil_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconcilHistory extends AssignedIdEntity {

    @Id
    private UUID id;
    
    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    // số tiền tổng của transaction
    @Column(name = "total_transaction", nullable = false)
    private Long toltalTxn;

    // số tiền ban đầu của wallet
    @Column(name = "wallet_balance", nullable = false)
    private Long walletBalance;
}

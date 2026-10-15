package com.datn.financeapp.module.transaction.entity;

import com.datn.financeapp.common.abstracts.AssignedIdEntity;
import com.datn.financeapp.module.transaction.enums.RecTxnScheduleStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.UUID;

@Builder
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "recurring_transaction_schedules")
public class RecTxnSchedule extends AssignedIdEntity {
    
    @Id
    private UUID id;

    // thời gian dự kiến chạy (người dùng có thể sửa đổi riêng buổi này)
    @Column(name = "run_at", nullable = false)
    private Instant runAt;

    // số tiền riêng của buổi này (mặc định lấy từ cha, có thể sửa riêng)
    @Column(name = "amount", nullable = false)
    private Long amount;

    // trạng thái: PENDING (chờ chạy), COMPLETED (đã tạo txn), SKIPPED (người dùng hủy buổi này)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RecTxnScheduleStatus status;


    @Column(name = "user_id")
    private UUID userId;

    //     bên RecurringTransactionSchedule
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recurring_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private RecTxn recTxn;


}
package com.datn.financeapp.group.entity;

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
 * Số dư tích luỹ của một thành viên trong nhóm (bảng {@code group_member_balances}, V18).
 * <p>
 * Chỉ tính giao dịch {@code CONFIRMED} chưa xoá. Số liệu chỉ được cộng dồn qua
 * {@code MemberBalanceRepository.addDelta}; không gán field rồi để Hibernate flush,
 * vì như vậy sẽ ghi đè số mà lệnh khác vừa cộng.
 * </p>
 */
@Entity
@Table(name = "group_member_balances")
@IdClass(MemberBalance.MemberBalanceId.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberBalance {

    @Id
    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * Tổng tiền túi người này đã chi hộ nhóm (EXPENSE nguồn PERSONAL).
     */
    @Column(name = "paid_out_of_pocket", nullable = false)
    private long paidOutOfPocket;

    /**
     * Tổng tiền người này đã nộp quỹ (CONTRIBUTION).
     */
    @Column(name = "contribution", nullable = false)
    private long contribution;

    /**
     * Tổng tiền quỹ đã trả lại cho người này (REFUND).
     */
    @Column(name = "refund", nullable = false)
    private long refund;

    /**
     * Tổng phần người này phải chịu trong các khoản chia tiền; ADJUSTMENT_UP làm giảm phần này.
     */
    @Column(name = "share", nullable = false)
    private long share;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class MemberBalanceId implements Serializable {
        private UUID groupId;
        private UUID userId;
    }
}

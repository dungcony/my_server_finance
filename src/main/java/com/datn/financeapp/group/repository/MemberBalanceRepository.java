package com.datn.financeapp.group.repository;

import com.datn.financeapp.group.entity.MemberBalance;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * Repository cho bảng tổng hợp {@code group_member_balances} (V18).
 * <p>
 * Vắng dòng nghĩa là thành viên chưa có giao dịch nào được tính, tức mọi chỉ số bằng 0.
 * </p>
 */
public interface MemberBalanceRepository extends JpaRepository<MemberBalance, MemberBalance.MemberBalanceId> {

    List<MemberBalance> findByGroupId(UUID groupId);

    /**
     * Đọc số dư của các thành viên chỉ định và khoá các dòng đó tới hết transaction,
     * dùng khi kiểm hạn mức hoàn tiền để lệnh khác không chen vào giữa bước kiểm và bước ghi.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b
            from MemberBalance b
            where b.groupId = :groupId
              and b.userId in :userIds
            order by b.userId
            """)
    List<MemberBalance> findForUpdate(UUID groupId, Collection<UUID> userIds);

    /**
     * Cộng dồn chênh lệch vào số dư của một thành viên, chèn dòng mới nếu chưa có.
     * <p>
     * Cộng ngay trong câu SQL thay vì đọc rồi ghi ở service để không ghi đè số mà lệnh khác vừa cộng.
     * </p>
     */
    @Modifying
    @Query(value = """
            INSERT INTO group_member_balances (group_id, user_id, paid_out_of_pocket, contribution, refund, share)
            VALUES (:groupId, :userId, :paidOutOfPocket, :contribution, :refund, :share)
            ON CONFLICT (group_id, user_id) DO UPDATE SET
                paid_out_of_pocket = group_member_balances.paid_out_of_pocket + EXCLUDED.paid_out_of_pocket,
                contribution       = group_member_balances.contribution + EXCLUDED.contribution,
                refund             = group_member_balances.refund + EXCLUDED.refund,
                share              = group_member_balances.share + EXCLUDED.share
            """, nativeQuery = true)
    void addDelta(UUID groupId, UUID userId, long paidOutOfPocket, long contribution, long refund, long share);
}

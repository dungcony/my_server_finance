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
public interface MemberBalanceRepository extends JpaRepository<MemberBalance, MemberBalance.MemberBalanceId>, MemberBalanceRepositoryCustom {

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
}

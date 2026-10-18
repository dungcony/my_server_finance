package com.datn.financeapp.module.wallet.repository;

import com.datn.financeapp.module.group.helper.GroupTxnSpecification;
import com.datn.financeapp.module.wallet.entity.Wallet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID>, JpaSpecificationExecutor<Wallet> {

    long countByUserIdAndIsDeletedFalse(UUID userId);

    boolean existsByUserIdAndNameAndIsDeletedFalse(UUID userId, String name);

    @Modifying
    @Query("""
            insert into Wallet (id, userId, name, type, balance, iconCode, color, isDefault, isDeleted)
            select :#{#w.id}, :#{#w.userId}, :#{#w.name}, :#{#w.type}, :#{#w.balance},
                   :#{#w.iconCode}, :#{#w.color}, :#{#w.isDefault}, false
            where not exists (
                select 1 from Wallet x
                where x.userId = :#{#w.userId}
                  and x.name = :#{#w.name}
                  and x.isDeleted = false)
            """)
    int insertIfNameNotExist(@Param("w") Wallet wallet);

    Optional<Wallet> findByIdAndUserIdAndIsDeleted(UUID id, UUID userId, boolean isDeleted);

    Page<Wallet> findAllByUserIdAndIsDeleted(UUID userId, Boolean isDeleted, Pageable pageable);

    /**
     * Đối chiếu số dư của mọi ví chưa xoá theo tổng giao dịch, chỉ sửa những ví đang lệch và ghi mỗi ví đã sửa
     * một dòng vào {@code reconcil_history} (số dư cũ, số dư đúng) trong cùng câu lệnh.
     */
    @Modifying
    @Query(
            value = """
                    WITH totals AS (
                        SELECT w.id,
                               w.balance AS old_balance,
                               COALESCE(SUM(CASE
                                   WHEN t.type = 'INCOME' THEN t.amount
                                   WHEN t.type IN ('EXPENSE', 'LOAN') THEN -t.amount
                                   ELSE 0 END), 0) AS total
                        FROM wallets w
                        LEFT JOIN transactions t ON t.wallet_id = w.id AND NOT t.is_deleted
                        WHERE NOT w.is_deleted
                        GROUP BY w.id, w.balance
                    ),
                    fixed AS (
                        UPDATE wallets w
                        SET balance = totals.total
                        FROM totals
                        WHERE w.id = totals.id
                          AND w.balance <> totals.total
                        RETURNING w.id, totals.old_balance, totals.total
                    )
                    INSERT INTO reconcil_history (wallet_id, wallet_balance, total_transaction)
                    SELECT id, old_balance, total FROM fixed
                    """,
            nativeQuery = true
    )
    void reconcileAll();

    Optional<Wallet> findByIdAndUserId(UUID walletId, UUID userId);

    Optional<Wallet> findByIdAndIsDeletedFalse(UUID walletId);
}

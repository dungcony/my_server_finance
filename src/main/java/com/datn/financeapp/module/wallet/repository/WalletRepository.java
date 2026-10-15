package com.datn.financeapp.module.wallet.repository;

import com.datn.financeapp.module.wallet.entity.Wallet;
import com.datn.financeapp.module.wallet.enums.WalletType;
import com.google.common.io.Files;
import jakarta.persistence.LockModeType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    long countByUserIdAndIsDeletedFalse(UUID userId);

    boolean existsByUserIdAndNameIgnoreCaseAndTypeAndIsDeletedFalse(UUID userId, String name, WalletType type);

    Optional<Wallet> findByIdAndUserIdAndIsDeletedFalse(UUID id, UUID userId);

    Optional<Wallet> findByUserIdAndIsDefaultTrue(UUID userId);

    Page<Wallet> findAllByUserId(UUID userId, Pageable pageable);

    /**
     * Đối chiếu và cập nhật lại số dư ví theo tổng các giao dịch hợp lệ (Thu - Chi/Vay).
     *
     * @param walletId ID của ví cần đối chiếu
     * @return số dòng được cập nhật
     */
    @Modifying
    @Query(
            value = """
                    UPDATE wallets w
                    SET balance = COALESCE((
                        SELECT SUM(CASE
                            WHEN t.type = 'INCOME' THEN t.amount
                            WHEN t.type IN ('EXPENSE', 'LOAN') THEN -t.amount
                            ELSE 0 END)
                        FROM transactions t
                        WHERE t.wallet_id = w.id AND NOT t.is_deleted
                    ), 0)
                    WHERE w.id = :walletId
                    """,
            nativeQuery = true
    )
    int reconcil(@Param("walletId") UUID walletId);

    @Query("""
            select w.id from Wallet w
            """)
    List<UUID> findAllIds();

    Optional<Wallet> findByIdAndIsDefaultTrue(UUID id);
}

package com.datn.financeapp.module.wallet.repository;

import com.datn.financeapp.module.wallet.entity.Wallet;
import com.datn.financeapp.module.wallet.enums.WalletType;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id = :id AND NOT w.isDeleted")
    Optional<Wallet> findByIdForUpdate(@Param("id") UUID id);

    Optional<Wallet> findByIdAndUserIdAndIsDeletedFalse(UUID id, UUID userId);

    /**
     * Atomic UPDATE số dư — KHÔNG load-modify-save (CLAUDE.md backend mục 3). {@code delta} có
     * thể âm (trừ) hoặc dương (cộng).
     */
    @Modifying
    @Query("UPDATE Wallet w SET w.currentBalance = w.currentBalance + :delta WHERE w.id = :id")
    int adjustBalance(@Param("id") UUID id, @Param("delta") long delta);

    @Query(value = "SELECT current_balance FROM wallets WHERE id = :id", nativeQuery = true)
    Optional<Long> findCurrentBalanceNative(@Param("id") UUID id);


    @Query(
            value = """
                    SELECT w.current_balance
                    - COALESCE(SUM(CASE
                    WHEN t.type = 'INCOME' THEN t.amount
                    WHEN t.type IN ('EXPENSE', 'LOAN') THEN -t.amount END), 0) AS balance_as_of
                    FROM wallets w
                    LEFT JOIN transactions t ON t.wallet_id = w.id AND t.date > :asOf AND NOT t.is_deleted
                    WHERE w.id = :walletId
                    GROUP BY w.id, w.current_balance
                    """,
            nativeQuery = true)
    Optional<Long> findBalanceAsOf(@Param("walletId") UUID walletId, @Param("asOf") LocalDate asOf);

    @Query(
            value = """
                    SELECT EXISTS(SELECT 1 FROM transactions
                    WHERE wallet_id = :walletId AND date > CURRENT_DATE AND NOT is_deleted)
                    """,
            nativeQuery = true)
    boolean hasFutureTransactions(@Param("walletId") UUID walletId);

    @Query(value = "SELECT id FROM wallets WHERE NOT is_deleted", nativeQuery = true)
    List<UUID> findAllActiveWalletIds();

    Page<Wallet> findAllByUserId(UUID userId, Pageable pageable);

    Optional<Wallet> findByUserIdAndIsDefaultTrue(UUID userId);
}

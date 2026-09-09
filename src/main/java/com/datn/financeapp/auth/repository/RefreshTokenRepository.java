package com.datn.financeapp.auth.repository;

import com.datn.financeapp.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * AUTH-03: rotation + reuse detection cho refresh token.
 *
 * {@link #findActiveByTokenHashForUpdate(String)} dùng {@code PESSIMISTIC_WRITE} (SELECT ...
 * FOR UPDATE) — khi 2 transaction cùng refresh 1 token, transaction thứ hai BLOCK tới khi
 * transaction đầu commit (đã revoke), lúc đó điều kiện WHERE revokedAt IS NULL không còn khớp
 * nên transaction thứ hai đọc về rỗng, tự nhiên rơi vào nhánh reuse-detection.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.tokenHash = :hash AND rt.revokedAt IS NULL")
    Optional<RefreshToken> findActiveByTokenHashForUpdate(@Param("hash") String hash);

    // Không lock — chỉ dùng khi đã biết token inactive, để tra userId cho reuse detection.
    Optional<RefreshToken> findByTokenHash(String hash);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revokedAt = CURRENT_TIMESTAMP "
            + "WHERE rt.userId = :userId AND rt.revokedAt IS NULL")
    int revokeAllActiveForUser(@Param("userId") UUID userId);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revokedAt = CURRENT_TIMESTAMP "
            + "WHERE rt.tokenHash = :hash AND rt.revokedAt IS NULL")
    int revokeByTokenHash(@Param("hash") String hash);

    List<RefreshToken> findAllByUserIdAndRevokedAtIsNull(UUID userId);
}

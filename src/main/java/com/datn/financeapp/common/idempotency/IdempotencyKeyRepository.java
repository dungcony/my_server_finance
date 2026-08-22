package com.datn.financeapp.common.idempotency;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository cho {@link IdempotencyKeyEntity}, khớp bảng {@code idempotency_keys} (V7).
 *
 * {@code @Modifying} native query cần chạy trong context {@code @Transactional} của tầng
 * gọi (Aspect/Job) — Spring Data JPA không tự mở transaction cho native modifying query.
 */
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKeyEntity, UUID> {

    @Modifying
    @Query(
            value =
                    """
        INSERT INTO idempotency_keys (id, idempotency_key, user_id, endpoint, status, created_at)
        VALUES (gen_random_uuid(), :key, :userId, :endpoint, 'processing', now())
        ON CONFLICT (idempotency_key, user_id, endpoint) DO NOTHING
        """,
            nativeQuery = true)
    int tryInsertProcessing(
            @Param("key") String key, @Param("userId") UUID userId, @Param("endpoint") String endpoint);

    Optional<IdempotencyKeyEntity> findByIdempotencyKeyAndUserIdAndEndpoint(
            String idempotencyKey, UUID userId, String endpoint);

    @Modifying
    void deleteByIdempotencyKeyAndUserIdAndEndpoint(String idempotencyKey, UUID userId, String endpoint);

    @Modifying
    @Query(value = "DELETE FROM idempotency_keys WHERE created_at < :cutoff", nativeQuery = true)
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}

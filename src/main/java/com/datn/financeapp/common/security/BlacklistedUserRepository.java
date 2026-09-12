package com.datn.financeapp.common.security;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface BlacklistedUserRepository extends CrudRepository<BlacklistedUserModel, String> {

    default boolean isBlacklisted(String userId) {
        return existsById(userId);
    }

    default void blacklist(UUID userId, String reason, long ttlSeconds) {
        save(BlacklistedUserModel.builder()
                .userId(userId)
                .reason(reason)
                .ttl(ttlSeconds)
                .blockedAt(Instant.now())
                .build());
    }
}

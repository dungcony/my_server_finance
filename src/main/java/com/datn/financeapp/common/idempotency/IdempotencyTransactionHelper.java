package com.datn.financeapp.common.idempotency;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bọc riêng các thao tác ghi {@code idempotency_keys} trong {@code @Transactional} — tách khỏi
 * {@link IdempotencyAspect} vì self-invocation trong cùng bean {@code @Aspect} không đi qua proxy
 * Spring AOP, khiến {@code @Transactional} bị bỏ qua nếu gọi trực tiếp method nội bộ.
 */
@Component
@RequiredArgsConstructor
public class IdempotencyTransactionHelper {

    private final IdempotencyKeyRepository repository;

    @Transactional
    public int tryInsertProcessing(String key, UUID userId, String endpoint) {
        return repository.tryInsertProcessing(key, userId, endpoint);
    }

    @Transactional
    public void deleteRecord(String key, UUID userId, String endpoint) {
        repository.deleteByIdempotencyKeyAndUserIdAndEndpoint(key, userId, endpoint);
    }

    @Transactional
    public void save(IdempotencyKeyEntity entity) {
        repository.save(entity);
    }
}

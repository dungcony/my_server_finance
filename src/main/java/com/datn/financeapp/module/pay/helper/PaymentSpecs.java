package com.datn.financeapp.module.pay.helper;

import com.datn.financeapp.module.pay.entity.Payment;
import com.datn.financeapp.module.pay.enums.PaymentStatus;
import com.datn.financeapp.module.pay.enums.PaymentType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bộ lọc động cho lịch sử thanh toán; tham số null thì bỏ qua điều kiện đó.
 */
public final class PaymentSpecs {

    private PaymentSpecs() {
    }

    /**
     * @param createdFrom tính cả mốc này
     * @param createdTo   không tính mốc này
     */
    public static Specification<Payment> filter(UUID userId, PaymentStatus status, PaymentType type,
                                                Instant createdFrom, Instant createdTo) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (createdFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
            }
            if (createdTo != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), createdTo));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

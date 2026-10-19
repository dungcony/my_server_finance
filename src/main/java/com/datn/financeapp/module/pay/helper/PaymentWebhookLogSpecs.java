package com.datn.financeapp.module.pay.helper;

import com.datn.financeapp.module.pay.entity.PaymentWebhookLog;
import com.datn.financeapp.module.pay.enums.WebhookResult;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Bộ lọc động cho nhật ký webhook; tham số null/false thì bỏ qua điều kiện đó.
 */
public final class PaymentWebhookLogSpecs {

    private PaymentWebhookLogSpecs() {
    }

    /**
     * @param onlyFailed true thì chỉ lấy nhóm lỗi ({@link WebhookResult#failures()})
     * @param from       tính cả mốc này
     * @param to         không tính mốc này
     */
    public static Specification<PaymentWebhookLog> filter(WebhookResult result, boolean onlyFailed, Long orderCode,
                                                          Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (result != null) {
                predicates.add(cb.equal(root.get("result"), result));
            }
            if (onlyFailed) {
                predicates.add(root.get("result").in(WebhookResult.failures()));
            }
            if (orderCode != null) {
                predicates.add(cb.equal(root.get("orderCode"), orderCode));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("receivedAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("receivedAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

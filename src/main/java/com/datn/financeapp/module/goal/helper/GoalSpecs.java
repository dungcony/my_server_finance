package com.datn.financeapp.module.goal.helper;

import com.datn.financeapp.module.goal.dto.request.GoalFilterRequest;
import com.datn.financeapp.module.goal.entity.SavingsGoal;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Bộ lọc động (JPA Specification) cho mục tiêu tiết kiệm dành cho Admin.
 */
public final class GoalSpecs {

    private GoalSpecs() {
    }

    public static Specification<SavingsGoal> filter(GoalFilterRequest filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));

            if (filter == null) {
                return cb.and(predicates.toArray(new Predicate[0]));
            }

            if (filter.userId() != null) {
                predicates.add(cb.equal(root.get("userId"), filter.userId()));
            }

            if (filter.status() != null && !filter.status().isBlank()) {
                predicates.add(cb.equal(root.get("status"), filter.status().trim()));
            }

            if (filter.name() != null && !filter.name().isBlank()) {
                String pattern = "%" + filter.name().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.like(cb.lower(root.get("name")), pattern));
            }

            if (filter.targetDateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("targetDate"), filter.targetDateFrom()));
            }

            if (filter.targetDateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("targetDate"), filter.targetDateTo()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<SavingsGoal> filter(UUID userId, String status, String name) {
        return filter(new GoalFilterRequest(userId, status, name, null, null));
    }
}

package com.datn.financeapp.module.goal.helper;

import com.datn.financeapp.module.goal.dto.request.ContributionGoalFilterReq;
import com.datn.financeapp.module.goal.dto.request.SavingGoalFilterReq;
import com.datn.financeapp.module.goal.entity.GoalContribution;
import com.datn.financeapp.module.goal.entity.SavingGoal;
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

    public static Specification<SavingGoal> filter(UUID userId, SavingGoalFilterReq filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));

            if (filter == null) {
                return cb.and(predicates.toArray(new Predicate[0]));
            }

            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }

            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
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

}

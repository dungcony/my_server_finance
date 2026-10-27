package com.datn.financeapp.module.goal.helper;

import com.datn.financeapp.module.goal.dto.request.ContributionGoalFilterReq;
import com.datn.financeapp.module.goal.entity.GoalContribution;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bộ lọc động (JPA Specification) cho lịch sử đóng góp và rút tiền của mục tiêu tiết kiệm.
 */
public final class ContributionGoalSpecs {

    private ContributionGoalSpecs() {
    }

    public static Specification<GoalContribution> filter(UUID goalId, ContributionGoalFilterReq filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));

            if (goalId != null) {
                predicates.add(cb.equal(root.get("goalId"), goalId));
            }

            if (filter == null) {
                return cb.and(predicates.toArray(new Predicate[0]));
            }

            if (filter.isWithdrawal() != null) {
                if (filter.isWithdrawal()) {
                    predicates.add(cb.lessThan(root.get("amount"), 0L));
                } else {
                    predicates.add(cb.greaterThan(root.get("amount"), 0L));
                }
            }

            if (filter.minAmount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(cb.abs(root.get("amount")), filter.minAmount()));
            }

            if (filter.maxAmount() != null) {
                predicates.add(cb.lessThanOrEqualTo(cb.abs(root.get("amount")), filter.maxAmount()));
            }

            if (filter.contributedDateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("contributedDate"), filter.contributedDateFrom()));
            }

            if (filter.contributedDateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("contributedDate"), filter.contributedDateTo()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
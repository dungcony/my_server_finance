package com.datn.financeapp.group.repository.impl;

import com.datn.financeapp.group.entity.GTransaction;
import com.datn.financeapp.group.repository.GroupTransactionRepositoryCustom;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class GroupTransactionRepositoryCustomImpl implements GroupTransactionRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<GTransaction> findList(Specification<GTransaction> spec, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<GTransaction> query = cb.createQuery(GTransaction.class);
        Root<GTransaction> root = query.from(GTransaction.class);

        // áp dụng điều kiện lọc từ specification
        if (spec != null) {
            Predicate predicate = spec.toPredicate(root, query, cb);
            if (predicate != null) {
                query.where(predicate);
            }
        }

        TypedQuery<GTransaction> typedQuery = entityManager.createQuery(query);

        // phân trang trực tiếp ở tầng database bằng limit và offset
        if (pageable != null && pageable.isPaged()) {
            typedQuery.setFirstResult((int) pageable.getOffset());
            typedQuery.setMaxResults(pageable.getPageSize());
        }

        return typedQuery.getResultList();
    }
}

package com.datn.financeapp.module.group.repository.impl;

import com.datn.financeapp.module.group.entity.GroupTxn;
import com.datn.financeapp.module.group.repository.GroupTxnRepositoryCustom;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.QueryUtils;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class GroupTxnRepositoryCustomImpl implements GroupTxnRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<GroupTxn> findList(Specification<GroupTxn> spec, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<GroupTxn> query = cb.createQuery(GroupTxn.class);
        Root<GroupTxn> root = query.from(GroupTxn.class);

        // Áp dụng điều kiện lọc WHERE
        if (spec != null) {
            Predicate predicate = spec.toPredicate(root, query, cb);
            if (predicate != null) {
                query.where(predicate);
            }
        }

        // Áp dụng ORDER BY (BẮT BUỘC đặt trước entityManager.createQuery)
        if (pageable != null && pageable.getSort().isSorted()) {
            query.orderBy(QueryUtils.toOrders(pageable.getSort(), root, cb));
        }

        // Khởi tạo TypedQuery sau khi query đã có đủ WHERE và ORDER BY
        TypedQuery<GroupTxn> typedQuery = entityManager.createQuery(query);

        // Áp dụng LIMIT / OFFSET
        if (pageable != null && pageable.isPaged()) {
            typedQuery.setFirstResult((int) pageable.getOffset());
            typedQuery.setMaxResults(pageable.getPageSize());
        }

        return typedQuery.getResultList();
    }
}

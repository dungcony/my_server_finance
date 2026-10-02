package com.datn.financeapp.group.repository.specification;

import com.datn.financeapp.group.dto.request.transaction.GroupTransactionFilterReq;
import com.datn.financeapp.group.entity.GTransaction;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GTransactionSpecification {

    /**
     * - root đại diện cho câu from
     * + root.get(feild) để lấy feild
     * - cb bộ chế tạo biểu thức để tạo ra các phép so sánh
     * - query tcaasu hình các phần mở rộng như orderBy, groupBy
     * - predicate đại diện cho mỗi 1 câu điều kiện
     */
    public static Specification<GTransaction> filter(UUID groupId, GroupTransactionFilterReq filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // điều kiện bắt buộc
            predicates.add(cb.equal(root.get("groupId"), groupId));
            predicates.add(cb.isNull(root.get("deletedAt")));

            // điều kiện lọc theo nguồn tiền
            if (filter.moneySource() != null)
                predicates.add(cb.equal(root.get("moneySource"), filter.moneySource()));

            // điều kiện lọc theo loại giao dịch
            if (filter.type() != null)
                predicates.add(cb.equal(root.get("type"), filter.type()));


            // điều kiện lọc theo trạng thái
            if (filter.status() != null)
                predicates.add(cb.equal(root.get("status"), filter.status()));

            // loại trừ trạng thái (dùng cho member thường không thấy PENDING)
            if (filter.excludeStatus() != null)
                predicates.add(cb.notEqual(root.get("status"), filter.excludeStatus()));


            // điều kiện lọc theo người chi
            if (filter.transactorId() != null)
                predicates.add(cb.equal(root.get("transactorId"), filter.transactorId()));

            // điều kiện lọc theo người tạo
            if (filter.createdBy() != null)
                predicates.add(cb.equal(root.get("createdBy"), filter.createdBy()));


            // điều kiện lọc theo khoảng thời gian
            if (filter.resolveFrom() != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), filter.resolveFrom()));

            if (filter.resolveTo() != null)
                predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), filter.resolveTo()));


            // sắp xếp theo thời gian giao dịch mới nhất
            query.orderBy(cb.desc(root.get("occurredAt")), cb.desc(root.get("createdAt")));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

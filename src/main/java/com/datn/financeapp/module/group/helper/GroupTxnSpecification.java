package com.datn.financeapp.module.group.helper;

import com.datn.financeapp.module.group.dto.request.transaction.GroupTxnFilterReq;
import com.datn.financeapp.module.group.entity.GroupTxn;
import com.datn.financeapp.module.group.enums.GroupTxnStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GroupTxnSpecification {

    /**
     * - root đại diện cho câu from
     * + root.get(feild) để lấy feild
     * - cb bộ chế tạo biểu thức để tạo ra các phép so sánh
     * - query tcaasu hình các phần mở rộng như orderBy, groupBy
     * - predicate đại diện cho mỗi 1 câu điều kiện
     */
    public static Specification<GroupTxn> filter(UUID groupId, UUID myId, List<GroupTxnStatus> statuses, GroupTxnFilterReq filter) {
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
            if (statuses != null && !statuses.isEmpty())
                predicates.add(root.get("status").in(statuses));

            // điều kiện lọc theo người chi
            if (filter.transactorId() != null)
                predicates.add(cb.equal(root.get("transactorId"), filter.transactorId()));

            // điều kiện lọc theo người chi
            if (filter.createId() != null)
                predicates.add(cb.equal(root.get("createdBy"), filter.createId()));

            // điều kiện lọc giao dịch liên quan tới một người: người tạo hoặc người thực hiện
            if (myId != null)
                predicates.add(cb.or(
                        cb.equal(root.get("createdBy"), myId),
                        cb.equal(root.get("transactorId"), myId)));


            // điều kiện lọc theo khoảng thời gian
            if (filter.resolveFrom() != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), filter.resolveFrom()));

            if (filter.resolveTo() != null)
                predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), filter.resolveTo()));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

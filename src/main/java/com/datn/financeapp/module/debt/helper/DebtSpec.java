package com.datn.financeapp.module.debt.helper;

import com.datn.financeapp.module.debt.dto.request.DebtFilterReq;
import com.datn.financeapp.module.debt.entity.Debt;
import com.datn.financeapp.module.debt.enums.DebtStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Bộ lọc động (JPA Specification) cho danh sách khoản nợ (debts).
 */
public final class DebtSpec {

    private DebtSpec() {
    }

    /**
     * Tạo Specification lọc danh sách khoản nợ theo user và các điều kiện lọc.
     *
     * @param userId ID người dùng sở hữu (truyền null nếu lọc toàn hệ thống cho Admin)
     * @param filter Bộ tiêu chí lọc {@link DebtFilterReq}
     * @return Specification tương ứng
     */
    public static Specification<Debt> filter(UUID userId, DebtFilterReq filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Luôn loại bỏ các bản ghi đã xóa mềm
            predicates.add(cb.isFalse(root.get("isDeleted")));

            // 2. Lọc theo user nếu có
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }

            if (filter == null) {
                return cb.and(predicates.toArray(new Predicate[0]));
            }

            // 3. Tìm kiếm theo tên / đối tác (không phân biệt hoa thường)
            if (filter.name() != null && !filter.name().isBlank()) {
                String pattern = "%" + filter.name().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.like(cb.lower(root.get("name")), pattern));
            }

            // 4. Lọc theo loại nợ (LENDING / BORROWING)
            if (filter.type() != null) {
                predicates.add(cb.equal(root.get("type"), filter.type()));
            }

            // 5. Lọc theo trạng thái nợ (INPROCESS / COMPLETED)
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }

            // 6. Lọc theo ví liên kết
            if (filter.walletId() != null) {
                predicates.add(cb.equal(root.get("walletId"), filter.walletId()));
            }

            // 7. Lọc theo ngày phát sinh nợ (issued_date)
            if (filter.issuedDateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("issuedDate"), filter.issuedDateFrom()));
            }
            if (filter.issuedDateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("issuedDate"), filter.issuedDateTo()));
            }

            // 8. Lọc theo hạn trả (due_date)
            if (filter.dueDateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dueDate"), filter.dueDateFrom()));
            }
            if (filter.dueDateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dueDate"), filter.dueDateTo()));
            }

            // 9. Lọc các khoản nợ quá hạn (đang INPROCESS và dueDate < hôm nay)
            if (Boolean.TRUE.equals(filter.isOverdue())) {
                predicates.add(cb.equal(root.get("status"), DebtStatus.INPROCESS));
                predicates.add(cb.isNotNull(root.get("dueDate")));
                predicates.add(cb.lessThan(root.get("dueDate"), LocalDate.now()));
            }

            // 10. Lọc theo khoảng số tiền gốc (total_amount)
            if (filter.minAmount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("totalAmount"), filter.minAmount()));
            }
            if (filter.maxAmount() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("totalAmount"), filter.maxAmount()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Tiện ích lọc không ràng buộc userId (dùng cho Admin).
     */
    public static Specification<Debt> filter(DebtFilterReq filter) {
        return filter(null, filter);
    }
}

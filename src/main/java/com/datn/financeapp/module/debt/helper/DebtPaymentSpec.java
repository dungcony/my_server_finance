package com.datn.financeapp.module.debt.helper;

import com.datn.financeapp.module.debt.dto.request.DebtPaymentFilterReq;
import com.datn.financeapp.module.debt.entity.DebtPayment;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Bộ lọc động (JPA Specification) cho danh sách đợt thanh toán nợ (debt_payments).
 */
public final class DebtPaymentSpec {

    private DebtPaymentSpec() {
    }

    /**
     * Tạo Specification lọc danh sách đợt thanh toán theo debtId và các điều kiện lọc.
     *
     * @param debtId ID khoản nợ cha (truyền null nếu muốn lọc tự do theo filter)
     * @param filter Tiêu chí lọc {@link DebtPaymentFilterReq}
     * @return Specification tương ứng cho {@link DebtPayment}
     */
    public static Specification<DebtPayment> filter(UUID debtId, DebtPaymentFilterReq filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Luôn loại bỏ các bản ghi đã xóa mềm
            predicates.add(cb.isFalse(root.get("isDeleted")));

            // 2. Lọc theo debtId nếu được truyền vào trực tiếp
            if (debtId != null) {
                predicates.add(cb.equal(root.get("debtId"), debtId));
            }

            if (filter == null) {
                return cb.and(predicates.toArray(new Predicate[0]));
            }

            // 3. Lọc theo debtId trong filter (nếu tham số debtId truyền vào là null)
            if (debtId == null && filter.debtId() != null) {
                predicates.add(cb.equal(root.get("debtId"), filter.debtId()));
            }

            // 4. Lọc theo khoảng ngày thanh toán (paid_date)
            if (filter.paidDateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("paidDate"), filter.paidDateFrom()));
            }
            if (filter.paidDateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("paidDate"), filter.paidDateTo()));
            }

            // 5. Lọc theo khoảng số tiền thanh toán (amount)
            if (filter.minAmount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), filter.minAmount()));
            }
            if (filter.maxAmount() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("amount"), filter.maxAmount()));
            }

            // 6. Tìm kiếm theo ghi chú (note, không phân biệt hoa thường)
            if (filter.note() != null && !filter.note().isBlank()) {
                String pattern = "%" + filter.note().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.like(cb.lower(root.get("note")), pattern));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Tiện ích lọc chỉ nhận filter (dùng cho Admin hoặc khi debtId đã nằm trong filter).
     */
    public static Specification<DebtPayment> filter(DebtPaymentFilterReq filter) {
        return filter(null, filter);
    }
}

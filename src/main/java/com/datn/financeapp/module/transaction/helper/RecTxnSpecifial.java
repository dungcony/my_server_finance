package com.datn.financeapp.module.transaction.helper;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.transaction.dto.request.RecTxnFilterReq;
import com.datn.financeapp.module.transaction.entity.RecTxn;
import com.datn.financeapp.module.transaction.enums.FrequencyType;
import com.datn.financeapp.module.transaction.enums.TxnType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lớp trợ giúp xây dựng điều kiện truy vấn động (JPA Specification) cho giao dịch định kỳ.
 */
public class RecTxnSpecifial {

    private static final char LIKE_ESCAPE = '\\';

    public static Specification<RecTxn> filter(RecTxnFilterReq filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter == null)
                return cb.and(predicates.toArray(new Predicate[0]));

            if (filter.userId() != null)
                predicates.add(cb.equal(root.get("userId"), filter.userId()));

            // điều kiện lọc theo loại giao dịch
            if (hasText(filter.type()))
                predicates.add(cb.equal(root.get("type"), toEnum(TxnType.class, filter.type(), "type")));

            // điều kiện lọc theo đơn vị lặp
            if (hasText(filter.frequency()))
                predicates.add(cb.equal(root.get("frequency"),
                        toEnum(FrequencyType.class, filter.frequency(), "frequency")));

            // điều kiện lọc theo ví
            if (filter.walletId() != null)
                predicates.add(cb.equal(root.get("walletId"), filter.walletId()));

            // điều kiện lọc theo danh mục
            if (hasText(filter.categoryCode()))
                predicates.add(cb.equal(root.get("categoryCode"), filter.categoryCode().trim()));

            // điều kiện lọc theo trạng thái bật/tắt
            if (filter.isEnabled() != null)
                predicates.add(cb.equal(root.get("isEnabled"), filter.isEnabled()));

            // điều kiện tìm kiếm từ khóa trong tên hoặc ghi chú
            if (hasText(filter.search()))
                predicates.add(matchesKeyword(root, cb, filter.search()));

            // điều kiện lọc theo khoảng số tiền
            if (filter.minAmount() != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), filter.minAmount()));

            if (filter.maxAmount() != null)
                predicates.add(cb.lessThanOrEqualTo(root.get("amount"), filter.maxAmount()));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // tìm kiếm từ khóa không phân biệt hoa thường trong tên hoặc ghi chú
    private static Predicate matchesKeyword(Root<RecTxn> root, CriteriaBuilder cb, String keyword) {
        String pattern = likePattern(keyword);
        return cb.or(
                cb.like(cb.lower(root.get("name")), pattern, LIKE_ESCAPE),
                cb.like(cb.lower(root.get("note")), pattern, LIKE_ESCAPE));
    }

    // chuẩn hóa và escape ký tự đại diện để tìm đúng ký tự đặc biệt
    private static String likePattern(String keyword) {
        String escaped = keyword.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    // chuyển đổi chuỗi sang enum an toàn với thông báo lỗi rõ ràng
    private static <E extends Enum<E>> E toEnum(Class<E> enumClass, String value, String field) {
        try {
            return Enum.valueOf(enumClass, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Giá trị của '" + field + "' không hợp lệ.");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

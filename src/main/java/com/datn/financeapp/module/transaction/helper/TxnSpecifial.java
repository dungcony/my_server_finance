package com.datn.financeapp.module.transaction.helper;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.transaction.dto.request.TxnFilterRequest;
import com.datn.financeapp.module.transaction.entity.Transaction;
import com.datn.financeapp.module.transaction.enums.TxnSource;
import com.datn.financeapp.module.transaction.enums.TxnType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.*;

/**
 * Lớp trợ giúp xây dựng điều kiện truy vấn động (JPA Specification) cho giao dịch cá nhân.
 * <p>
 * <b>Các hàm chính:</b>
 * <ul>
 *   <li>{@link #filter(UUID, TxnFilterRequest, Collection)}: Xây dựng điều kiện lọc kèm danh sách ID danh mục cây</li>
 *   <li>{@link #filter(UUID, TxnFilterRequest)}: Xây dựng điều kiện lọc mặc định cho một người dùng</li>
 * </ul>
 * </p>
 */
public class TxnSpecifial {

    private static final char LIKE_ESCAPE = '\\';

    public static Specification<Transaction> filter(
            UUID userId,
            TxnFilterRequest filter,
            Collection<UUID> categoryIds) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // điều kiện bắt buộc: chỉ lấy giao dịch thuộc sở hữu của người dùng và chưa xóa mềm
            if (userId != null)
                predicates.add(cb.equal(root.get("userId"), userId));

            predicates.add(cb.isFalse(root.get("isDeleted")));

            if (filter == null)
                return cb.and(predicates.toArray(new Predicate[0]));

            // điều kiện lọc theo khoảng thời gian phát sinh giao dịch
            if (filter.from() != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), filter.from()));

            if (filter.to() != null)
                predicates.add(cb.lessThan(root.get("occurredAt"), filter.to()));

            // điều kiện lọc theo loại giao dịch
            if (hasText(filter.type()))
                predicates.add(cb.equal(root.get("type"), toEnum(TxnType.class, filter.type(), "type")));


            // điều kiện lọc theo nguồn giao dịch
            if (hasText(filter.source()))
                predicates.add(cb.equal(root.get("source"), toEnum(TxnSource.class, filter.source(), "source")));

            // điều kiện lọc theo ví
            if (filter.walletId() != null)
                predicates.add(cb.equal(root.get("walletId"), filter.walletId()));

            // điều kiện lọc theo danh mục: nếu truyền danh mục cây thì lọc theo danh sách, ngược lại lấy trực tiếp categoryId
            if (categoryIds != null)
                predicates.add(categoryIds.isEmpty() ? cb.disjunction() : root.get("categoryId").in(categoryIds));
            else if (filter.categoryId() != null)
                predicates.add(cb.equal(root.get("categoryId"), filter.categoryId()));

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

    public static Specification<Transaction> filter(UUID userId, TxnFilterRequest filter) {
        return filter(userId, filter, null);
    }

    // tìm kiếm từ khóa không phân biệt hoa thường trong tên hoặc ghi chú
    private static Predicate matchesKeyword(Root<Transaction> root, CriteriaBuilder cb, String keyword) {
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
    private static <E extends Enum<E>> E toEnum(Class<E> enumType, String value, String field) {
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Giá trị của '" + field + "' không hợp lệ.");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

package com.datn.financeapp.module.transaction.helper;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.transaction.dto.request.TransactionFilterRequest;
import com.datn.financeapp.module.transaction.entity.Transaction;
import com.datn.financeapp.module.transaction.enums.TxnSource;
import com.datn.financeapp.module.transaction.enums.TxnType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Dựng điều kiện lọc {@link Specification} cho {@link Transaction}.
 * <ul>
 *   <li>{@link #filter(UUID, TransactionFilterRequest, LocalDate, LocalDate, Collection)}: gom mọi điều kiện lọc
 *   danh sách giao dịch của một người dùng, dùng được cho cả lấy danh sách lẫn đếm.</li>
 * </ul>
 */
public class TransactionSpecification {

    private static final char LIKE_ESCAPE = '\\';

    /**
     * Điều kiện lọc giao dịch của một người dùng. Luôn có hai điều kiện bắt buộc là đúng chủ sở hữu và
     * chưa xóa mềm; các điều kiện còn lại chỉ thêm khi giá trị tương ứng khác {@code null}. Không có sắp xếp
     * để dùng lại được cho đếm, sắp xếp truyền qua {@code Pageable}.
     *
     * @param userId      ID người dùng sở hữu giao dịch
     * @param filter      bộ lọc từ client; không đọc {@code period}, {@code countsInReport},
     *                    {@code includeTransfers} vì đã được xử lý ở service hoặc không còn trong mô hình
     * @param fromDate    ngày bắt đầu đã tính sẵn từ {@code period}/{@code fromDate}, {@code null} là không chặn
     * @param toDate      ngày kết thúc đã tính sẵn từ {@code period}/{@code toDate}, {@code null} là không chặn
     * @param categoryIds danh mục cần lọc kèm mọi danh mục con (lấy từ {@code CategoryService.findSelfAndDescendantIds}
     *                    để cộng gộp con vào cha); {@code null} là không lọc, rỗng là không khớp giao dịch nào
     * @return điều kiện lọc giao dịch
     * @throws BusinessException nếu {@code type} hoặc {@code source} trong bộ lọc không phải giá trị hợp lệ
     */
    public static Specification<Transaction> filter(
            UUID userId,
            TransactionFilterRequest filter,
            LocalDate fromDate,
            LocalDate toDate,
            Collection<UUID> categoryIds) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // điều kiện bắt buộc: chỉ thấy giao dịch của mình và chưa bị xóa mềm
            predicates.add(cb.equal(root.get("userId"), userId));
            predicates.add(cb.isFalse(root.get("isDeleted")));

            // điều kiện lọc theo khoảng ngày
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("date"), fromDate));
            }
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<LocalDate>get("date"), toDate));
            }

            // điều kiện lọc theo loại giao dịch
            if (hasText(filter.type())) {
                predicates.add(cb.equal(root.get("type"), toEnum(TxnType.class, filter.type(), "type")));
            }

            // điều kiện lọc theo nguồn giao dịch
            if (hasText(filter.source())) {
                predicates.add(cb.equal(root.get("source"), toEnum(TxnSource.class, filter.source(), "source")));
            }

            // điều kiện lọc theo ví
            if (filter.walletId() != null) {
                predicates.add(cb.equal(root.get("walletId"), filter.walletId()));
            }

            // điều kiện lọc theo danh mục: rỗng nghĩa là danh mục không tồn tại nên không khớp giao dịch nào
            if (categoryIds != null) {
                predicates.add(categoryIds.isEmpty() ? cb.disjunction() : root.get("categoryId").in(categoryIds));
            }

            // điều kiện tìm theo từ khóa
            if (hasText(filter.search())) {
                predicates.add(matchesKeyword(root, cb, filter.search()));
            }

            // điều kiện lọc theo khoảng số tiền
            if (filter.minAmount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Long>get("amount"), filter.minAmount()));
            }
            if (filter.maxAmount() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<Long>get("amount"), filter.maxAmount()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // Khớp từ khóa trong tên hoặc ghi chú, không phân biệt hoa thường.
    private static Predicate matchesKeyword(Root<Transaction> root, CriteriaBuilder cb, String keyword) {
        String pattern = likePattern(keyword);
        return cb.or(
                cb.like(cb.lower(root.<String>get("name")), pattern, LIKE_ESCAPE),
                cb.like(cb.lower(root.<String>get("note")), pattern, LIKE_ESCAPE));
    }

    // Escape ký tự đại diện của LIKE để từ khóa chứa % hoặc _ được tìm đúng nghĩa đen.
    private static String likePattern(String keyword) {
        String escaped = keyword.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    // Chấp nhận cả chữ thường lẫn hoa; không đưa lại giá trị client gửi vào thông báo lỗi.
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

package com.datn.financeapp.module.wallet.helper;

import com.datn.financeapp.module.wallet.dto.request.WalletFillterReq;
import com.datn.financeapp.module.wallet.entity.Wallet;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lớp trợ giúp xây dựng điều kiện truy vấn động (JPA Specification) cho ví.
 */
public class WalletSpecifial {

    private static final char LIKE_ESCAPE = '\\';

    public static Specification<Wallet> filter(WalletFillterReq filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter == null)
                return cb.and(predicates.toArray(new Predicate[0]));

            if (filter.userId() != null)
                predicates.add(cb.equal(root.get("userId"), filter.userId()));

            if (filter.isDeleted() != null)
                predicates.add(cb.equal(root.get("isDeleted"), filter.isDeleted()));

            // điều kiện tìm kiếm từ khoá trong tên ví
            if (hasText(filter.search()))
                predicates.add(cb.like(cb.lower(root.get("name")), likePattern(filter.search()), LIKE_ESCAPE));

            // điều kiện lọc theo loại ví và ví mặc định
            if (filter.type() != null)
                predicates.add(cb.equal(root.get("type"), filter.type()));

            if (filter.isDefault() != null)
                predicates.add(cb.equal(root.get("isDefault"), filter.isDefault()));

            // điều kiện lọc theo khoảng số dư
            if (filter.minBalance() != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("balance"), filter.minBalance()));

            if (filter.maxBalance() != null)
                predicates.add(cb.lessThanOrEqualTo(root.get("balance"), filter.maxBalance()));

            // điều kiện lọc theo khoảng thời gian tạo ví
            if (filter.createdFrom() != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.createdFrom()));

            if (filter.createdTo() != null)
                predicates.add(cb.lessThan(root.get("createdAt"), filter.createdTo()));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // chuẩn hóa và escape ký tự đại diện để tìm đúng ký tự đặc biệt
    private static String likePattern(String keyword) {
        String escaped = keyword.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

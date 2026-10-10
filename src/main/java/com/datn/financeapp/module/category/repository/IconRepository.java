package com.datn.financeapp.module.category.repository;

import com.datn.financeapp.module.category.entity.Icon;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Repository JPA cho {@link Icon} — kho biểu tượng hệ thống (api/03-DANH-MUC.md mục 8).
public interface IconRepository extends JpaRepository<Icon, UUID> {

    Optional<Icon> findByCodeAndIsActiveTrue(String code);

    /**
     * Biểu tượng đang dùng, lọc tùy chọn theo nhóm và từ khóa.
     *
     * @param iconGroup nhóm cần lọc, {@code null} là mọi nhóm
     * @param search    mẫu {@code LIKE} đã viết thường (vd. {@code %cà phê%}), {@code null} là không lọc
     */
    @Query(
            value = """
                    SELECT * FROM icons WHERE is_active
                    AND (CAST(:iconGroup AS text) IS NULL OR icon_group = CAST(:iconGroup AS text))
                    AND (CAST(:search AS text) IS NULL OR lower(display_name) LIKE CAST(:search AS text) OR lower(search_keywords) LIKE CAST(:search AS text))
                    ORDER BY icon_group, sort_order
                    """,
            nativeQuery = true)
    List<Icon> search(@Param("iconGroup") String iconGroup, @Param("search") String search);
}

package com.datn.financeapp.module.icon.repository;

import com.datn.financeapp.module.icon.entity.Icon;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Repository JPA cho {@link Icon} — kho biểu tượng hệ thống.
public interface IconRepository extends JpaRepository<Icon, UUID> {

    boolean existsByCode(String code);

    Optional<Icon> findByCodeAndIsActiveTrue(String code);

    Optional<Icon> findByIdAndIsActiveTrue(UUID id);

    List<Icon> findByIdInAndIsActiveTrue(Collection<UUID> ids);

    Slice<Icon> findByIsActiveTrue(Pageable pageable);

    /**
     * Biểu tượng đang dùng, lọc tùy chọn theo nhóm và từ khóa.
     *
     * @param iconGroup nhóm cần lọc, {@code null} là mọi nhóm
     * @param search    mẫu {@code LIKE} đã viết thường (vd. {@code %cà phê%}), {@code null} là không lọc
     * @return danh sách biểu tượng phù hợp
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

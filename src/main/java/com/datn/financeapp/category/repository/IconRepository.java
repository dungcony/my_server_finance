package com.datn.financeapp.category.repository;

import com.datn.financeapp.category.entity.Icon;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository JPA cho {@link Icon} — kho biểu tượng hệ thống (api/03-DANH-MUC.md mục 8). */
public interface IconRepository extends JpaRepository<Icon, UUID> {

    List<Icon> findByIconGroupAndIsActiveTrueOrderBySortOrderAsc(String iconGroup);

    List<Icon> findByIsActiveTrueOrderByIconGroupAscSortOrderAsc();

    @Query(
            value = "SELECT * FROM icons WHERE is_active "
                    + "AND (:iconGroup IS NULL OR icon_group = :iconGroup) "
                    + "AND (:search IS NULL OR lower(display_name) LIKE :search OR lower(search_keywords) LIKE :search) "
                    + "ORDER BY icon_group, sort_order",
            nativeQuery = true)
    List<Icon> search(@Param("iconGroup") String iconGroup, @Param("search") String searchPattern);
}

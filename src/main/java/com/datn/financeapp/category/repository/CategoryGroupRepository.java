package com.datn.financeapp.category.repository;

import com.datn.financeapp.category.entity.CategoryGroup;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

// Repository JPA cho {@link CategoryGroup} — danh sách cố định, chỉ đọc (api/03-DANH-MUC.md mục 7).
public interface CategoryGroupRepository extends JpaRepository<CategoryGroup, UUID> {

    List<CategoryGroup> findAllByOrderBySortOrderAsc();
}

package com.datn.financeapp.category.repository;

import com.datn.financeapp.category.entity.Category;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository JPA cho {@link Category}. Danh mục không có khái niệm nhóm gia đình — schema V1
 * không liên kết {@code categories.user_id} với {@code group_id} nào, nên KHÔNG áp dụng vế nhóm
 * của D-27 ở đây (khác với {@code WalletRepository}). Danh mục hệ thống ({@code user_id IS NULL})
 * luôn hiển thị cho mọi người theo api/03-DANH-MUC.md mục 9.
 */
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    /**
     * Bọc {@code fn_category_tree} (V6__va_loi_bao_mat.sql) thành 1 method dùng chung — Phase 3
     * (cộng gộp danh mục con vào cha ở mọi thống kê) và Phase 4 (ngân sách/báo cáo) gọi lại
     * nguyên vẹn, KHÔNG tự viết điều kiện lọc cây khác ở nơi khác.
     */
    @Query(value = "SELECT category_id FROM fn_category_tree(:categoryId)", nativeQuery = true)
    List<UUID> findCategoryTree(@Param("categoryId") UUID categoryId);

    /**
     * Danh mục hệ thống ({@code user_id IS NULL}) luôn hiển thị cho mọi người đã đăng nhập; danh
     * mục riêng chỉ hiển thị cho đúng chủ sở hữu — không có 403, không có quyền thì 404.
     */
    @Query(
            value = "SELECT * FROM categories c WHERE c.id = :id AND NOT c.is_deleted "
                    + "AND (c.user_id IS NULL OR c.user_id = :currentUser)",
            nativeQuery = true)
    Optional<Category> findByIdAndVisibleToUser(@Param("id") UUID id, @Param("currentUser") UUID currentUser);

    @Query(
            value = "SELECT * FROM categories c WHERE NOT c.is_deleted "
                    + "AND (c.user_id IS NULL OR c.user_id = :currentUser) "
                    + "AND (:type IS NULL OR c.type = :type) "
                    + "AND (:rootsOnly = FALSE OR c.parent_category_id IS NULL) "
                    + "ORDER BY c.sort_order",
            nativeQuery = true)
    List<Category> findAllForUser(
            @Param("currentUser") UUID currentUser,
            @Param("type") String type,
            @Param("rootsOnly") boolean rootsOnly);

    boolean existsByParentCategoryIdAndIsDeletedFalse(UUID parentId);

    @Query(
            value = "SELECT COALESCE(MAX(sort_order), -1) FROM categories "
                    + "WHERE NOT is_deleted "
                    + "AND (:parentCategoryId IS NULL AND parent_category_id IS NULL "
                    + "     AND (user_id = :currentUser OR user_id IS NULL) "
                    + "     OR parent_category_id = :parentCategoryId)",
            nativeQuery = true)
    Integer findMaxSortOrderInLevel(
            @Param("currentUser") UUID currentUser, @Param("parentCategoryId") UUID parentCategoryId);

    @Query(
            value = "SELECT EXISTS(SELECT 1 FROM categories WHERE NOT is_deleted AND type = :type "
                    + "AND lower(name) = lower(:name) AND id <> COALESCE(:excludeId, '00000000-0000-0000-0000-000000000000'::uuid) "
                    + "AND ((:parentCategoryId IS NULL AND parent_category_id IS NULL "
                    + "        AND COALESCE(user_id, '00000000-0000-0000-0000-000000000000'::uuid) "
                    + "            = COALESCE(:currentUser, '00000000-0000-0000-0000-000000000000'::uuid)) "
                    + "     OR (parent_category_id = :parentCategoryId)))",
            nativeQuery = true)
    boolean existsSiblingWithName(
            @Param("currentUser") UUID currentUser,
            @Param("parentCategoryId") UUID parentCategoryId,
            @Param("type") String type,
            @Param("name") String name,
            @Param("excludeId") UUID excludeId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Category c SET c.sortOrder = :order WHERE c.id = :id AND c.userId = :currentUser "
            + "AND ((:parentCategoryId IS NULL AND c.parentCategoryId IS NULL) OR c.parentCategoryId = :parentCategoryId)")
    int updateSortOrder(
            @Param("id") UUID id,
            @Param("order") int order,
            @Param("currentUser") UUID currentUser,
            @Param("parentCategoryId") UUID parentCategoryId);
}

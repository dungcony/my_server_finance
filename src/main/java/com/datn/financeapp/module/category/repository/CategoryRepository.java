package com.datn.financeapp.module.category.repository;

import com.datn.financeapp.module.category.entity.Category;
import com.datn.financeapp.module.category.helper.CategoryCodeView;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository JPA cho {@link Category}. Danh mục là cây nhiều cấp do admin quản lý, dùng chung cho
 * mọi người dùng nên không có điều kiện sở hữu; cây con cháu truy vấn theo cột {@code path}.
 */
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    /**
     * Mọi cặp (id, code), kể cả danh mục đã xóa mềm — code của danh mục đã xóa vẫn được giữ chỗ
     * nên không lọc {@code deleted}.
     */
    @Query("SELECT new com.datn.financeapp.module.category.helper.CategoryCodeView(c.id, c.code, c.isDeleted) FROM Category c")
    List<CategoryCodeView> findAllCodes();

    Optional<Category> findByCodeAndIsDeletedFalse(String code);

    /**
     * Các danh mục (kể cả đã xóa mềm — code được giữ chỗ) có {@code code} nằm trong tập cho trước, dùng
     * để dựng danh sách tổ tiên từ {@code path} (chuỗi code nối bằng {@code /}).
     */
    List<Category> findByCodeIn(Collection<String> codes);

    List<Category> findByParentIsNullAndIsDeletedFalseOrderByNameAsc();

    List<Category> findByParent_IdAndIsDeletedFalseOrderByNameAsc(UUID parentId);

    boolean existsByParent_IdAndIsDeletedFalse(UUID parentId);

    /**
     * Mọi danh mục con cháu (mọi cấp, kể cả đã xóa mềm để {@code path} luôn nhất quán) của nút có
     * đường dẫn {@code path}. So tiền tố bằng {@code left(...)} thay vì {@code LIKE} để ký tự
     * {@code _} trong code không bị hiểu thành ký tự đại diện.
     */
    @Query("SELECT c FROM Category c WHERE left(c.path, length(:path) + 1) = concat(:path, '/')")
    List<Category> findDescendants(String path);

    /**
     * Có danh mục nào khác (chưa xóa) trùng tên, không phân biệt hoa thường, không — tên là duy nhất
     * toàn cây. Bỏ qua chính {@code id} để sửa danh mục không tự trùng với nó.
     */
    boolean existsByNameIgnoreCaseAndIsDeletedFalseAndIdNot(String name, UUID id);

    Optional<Category> findByCode(String code);

    boolean existsByCode(@NotBlank(message = "Mã danh mục không được để trống.") @Size(max = 50, message = "Mã danh mục không được vượt quá 50 ký tự.") @Pattern(regexp = "^[a-z0-9_]+$", message = "Mã chỉ gồm chữ thường, số và dấu gạch dưới.") String code);
}

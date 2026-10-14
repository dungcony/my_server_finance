package com.datn.financeapp.module.category.service;

import com.datn.financeapp.module.category.dto.request.CategoryCreateReq;
import com.datn.financeapp.module.category.dto.request.CategoryUpdateReq;
import com.datn.financeapp.module.category.dto.response.CategoryResponse;

import java.util.List;

public interface CategoryBehavierService {
    /**
     * Tạo danh mục mới. Không có {@code parentCode} thì là danh mục gốc, có thì là con của danh mục đó.
     *
     * @param category thông tin danh mục cần tạo
     * @return danh mục vừa tạo
     */
    CategoryResponse create(CategoryCreateReq category);

    /**
     * Lấy các danh mục gốc (chưa xóa), sắp theo tên.
     *
     * @return danh sách danh mục gốc
     */
    List<CategoryResponse> getMasters();

    /**
     * Lấy các danh mục con trực tiếp (chưa xóa) của danh mục cha, sắp theo tên.
     *
     * @param parentCode mã của danh mục cha
     * @return danh sách danh mục con
     */
    List<CategoryResponse> getOfParent(String parentCode);

    /**
     * Cập nhật danh mục. Trường {@code null} trong yêu cầu được giữ nguyên; đổi cha thì
     * {@code level}/{@code path} của cả cây con được tính lại.
     *
     * @param cateCode mã danh mục cần cập nhật
     * @param req      thông tin cập nhật
     * @return danh mục sau khi cập nhật
     */
    CategoryResponse update(String cateCode, CategoryUpdateReq req);

    /**
     * Xóa mềm danh mục cùng toàn bộ danh mục con cháu; mã của các danh mục đã xóa vẫn được giữ chỗ.
     * Danh mục thay thế (nếu có) phải là lá và nằm ngoài cây bị xóa. Chưa chuyển giao dịch
     * sang danh mục thay thế.
     *
     * @param cateCode mã danh mục cần xóa
     */
    void delete(String cateCode);

}

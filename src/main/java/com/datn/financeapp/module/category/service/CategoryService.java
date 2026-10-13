package com.datn.financeapp.module.category.service;

import com.datn.financeapp.module.category.dto.request.CategoryCreateReq;
import com.datn.financeapp.module.category.dto.request.CategoryUpdateReq;
import com.datn.financeapp.module.category.dto.response.CategoryResponse;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service quản lý cây danh mục thu chi nhiều cấp do admin quản lý; người dùng thường chỉ đọc.
 * <p>
 * <b>Các hàm:</b>
 * <ul>
 *   <li>{@link #create(CategoryCreateReq)}: tạo danh mục gốc hoặc danh mục con.</li>
 *   <li>{@link #getMasters()}: lấy các danh mục gốc.</li>
 *   <li>{@link #getOfParent(String)}: lấy các danh mục con trực tiếp của một danh mục.</li>
 *   <li>{@link #update(String, CategoryUpdateReq)}: sửa thông tin hoặc đổi cha của danh mục.</li>
 *   <li>{@link #delete(String)}: xóa mềm danh mục cùng cả cây con.</li>
 *   <li>{@link #findActiveById(UUID)}: lấy một danh mục chưa xóa theo id.</li>
 *   <li>{@link #findAllByIds(Collection)}: lấy nhiều danh mục theo id trong một lượt.</li>
 *   <li>{@link #findSelfAndAncestorIds(UUID)}: id của danh mục và mọi tổ tiên.</li>
 *   <li>{@link #findSelfAndDescendantIds(UUID)}: id của danh mục và mọi con cháu.</li>
 * </ul>
 * </p>
 */
public interface CategoryService {

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

    /**
     * Lấy một danh mục chưa xóa theo id. Dành cho module khác (vd. ngân sách) kiểm tra danh mục tồn tại.
     *
     * @param id ID danh mục
     * @return danh mục tìm được
     * @throws com.datn.financeapp.common.exception.BusinessException nếu không có hoặc đã xóa
     */
    CategoryResponse findActiveById(UUID id);

    /**
     * Lấy id danh mục theo code, tra từ cache RAM (không query DB). Dành cho module khác cần gắn giao dịch vào một danh mục
     * hệ thống biết trước code (vd. giao dịch sinh từ nhóm).
     *
     * @param code code danh mục
     * @return ID danh mục
     * @throws com.datn.financeapp.common.exception.BusinessException nếu không có hoặc đã xóa
     */
    UUID findIdByCode(String code);

    /**
     * Lấy code của nhiều danh mục theo id trong một lượt, tra từ cache RAM nên không query DB.
     * Id không có trong cache bị bỏ qua.
     *
     * @param ids các ID danh mục
     * @return map id → code
     */
    Map<UUID, String> findCodesByIds(Collection<UUID> ids);

    /**
     * Lấy nhiều danh mục theo id trong một lượt, kể cả đã xóa mềm (bản ghi cũ vẫn cần hiển thị tên).
     * Id không tồn tại bị bỏ qua.
     *
     * @param ids các ID danh mục
     * @return map id → danh mục
     */
    Map<UUID, CategoryResponse> findAllByIds(Collection<UUID> ids);

    /**
     * Id của danh mục này cùng mọi tổ tiên của nó (đi lên tới gốc). Dùng để tìm các ngân sách phải
     * cộng gộp một giao dịch ghi ở danh mục con.
     *
     * @param id ID danh mục
     * @return danh sách id, rỗng nếu không có danh mục này
     */
    List<UUID> findSelfAndAncestorIds(UUID id);

    /**
     * Id của danh mục này cùng mọi con cháu (mọi cấp). Dùng để cộng gộp chi tiêu của cả cây vào danh mục cha.
     *
     * @param id ID danh mục
     * @return danh sách id, rỗng nếu không có danh mục này
     */
    List<UUID> findSelfAndDescendantIds(UUID id);
}

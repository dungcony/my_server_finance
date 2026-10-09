package com.datn.financeapp.module.category.service;

import com.datn.financeapp.module.category.dto.response.CategoryResponse;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service quản lý danh mục thu chi, cây danh mục hai cấp, cài đặt danh mục theo ví và quản lý biểu tượng.
 * <p>
 * <b>Các hàm chính:</b>
 * <ul>
 *   <li>{@link #list(UUID, String, boolean, boolean)}: Truy vấn danh sách danh mục chi tiêu/thu nhập.</li>
 *   <li>{@link #listForWallet(UUID, UUID)}: Lấy danh sách danh mục áp dụng riêng cho một ví.</li>
 *   <li>{@link #setCategoryEnabledForWallet(UUID, UUID, UUID, boolean)}: Bật/tắt trạng thái sử dụng danh mục ở ví.</li>
 *   <li>{@link #detail(UUID, UUID)}: Lấy chi tiết danh mục theo ID.</li>
 *   <li>{@link #create(UUID, CreateCategoryRequest)}: Tạo mới danh mục tùy chỉnh.</li>
 *   <li>{@link #update(UUID, UUID, UpdateCategoryRequest)}: Cập nhật danh mục.</li>
 *   <li>{@link #delete(UUID, UUID, UUID)}: Xóa danh mục và di chuyển giao dịch sang danh mục thay thế.</li>
 *   <li>{@link #reorder(UUID, ReorderCategoriesRequest)}: Thay đổi thứ tự sắp xếp danh mục.</li>
 *   <li>{@link #listCategoryGroups()}: Lấy danh sách nhóm danh mục hệ thống.</li>
 *   <li>{@link #listIcons(String, String)}: Truy vấn danh sách biểu tượng.</li>
 * </ul>
 * </p>
 */
public interface CategoryService {

    /**
     * Khối bọc danh sách nhóm biểu tượng kèm tổng số lượng.
     *
     * @param group Danh sách các nhóm biểu tượng
     * @param total Tổng số lượng biểu tượng tìm thấy
     */
    record IconGroupResponseWrapper(List<IconGroupResponse> group, int total) {
    }

    /**
     * Lấy danh sách danh mục thu chi của người dùng theo điều kiện lọc.
     *
     * @param userId    ID của người dùng
     * @param type      Loại danh mục ({@code expense} hoặc {@code income})
     * @param asTree    {@code true} nếu muốn trả về dạng cấu trúc cây hai cấp
     * @param rootsOnly {@code true} nếu chỉ muốn lấy danh mục cha
     * @return Danh sách các danh mục thỏa mãn
     */
    List<CategoryResponse> list(UUID userId, String type, boolean asTree, boolean rootsOnly);

    /**
     * Lấy danh sách danh mục kèm cờ trạng thái bật/tắt áp dụng riêng cho ví.
     *
     * @param userId   ID của người dùng
     * @param walletId ID của ví cần lấy cài đặt
     * @return Cây danh mục kèm trạng thái kích hoạt trên ví
     */
    List<CategoryResponse> listForWallet(UUID userId, UUID walletId);

    /**
     * Bật hoặc tắt trạng thái sử dụng của một danh mục tại ví chỉ định.
     *
     * @param userId     ID của người dùng
     * @param walletId   ID của ví áp dụng
     * @param categoryId ID của danh mục cần thao tác
     * @param enabled    {@code true} để bật, {@code false} để tắt
     */
    void setCategoryEnabledForWallet(UUID userId, UUID walletId, UUID categoryId, boolean enabled);

    /**
     * Lấy chi tiết thông tin của một danh mục.
     *
     * @param userId     ID của người dùng
     * @param categoryId ID danh mục
     * @return Chi tiết danh mục
     */
    CategoryDetailResponse detail(UUID userId, UUID categoryId);

    /**
     * Tạo mới một danh mục thu/chi tùy chỉnh cho người dùng.
     *
     * @param userId ID của người dùng
     * @param req    Thông tin yêu cầu tạo danh mục
     * @return Danh mục vừa tạo thành công
     */
    CategoryResponse create(UUID userId, CreateCategoryRequest req);

    /**
     * Cập nhật thông tin danh mục tùy chỉnh.
     *
     * @param userId     ID của người dùng
     * @param categoryId ID danh mục cần cập nhật
     * @param req        Thông tin yêu cầu cập nhật
     * @return Danh mục sau khi cập nhật
     */
    CategoryResponse update(UUID userId, UUID categoryId, UpdateCategoryRequest req);

    /**
     * Xóa danh mục tùy chỉnh và chuyển toàn bộ giao dịch sang danh mục thay thế.
     *
     * @param userId                ID của người dùng
     * @param categoryId            ID danh mục cần xóa
     * @param replacementCategoryId ID danh mục thay thế để nhận giao dịch chuyển giao
     */
    void delete(UUID userId, UUID categoryId, UUID replacementCategoryId);

    /**
     * Thay đổi vị trí/thứ tự sắp xếp của các danh mục.
     *
     * @param userId ID của người dùng
     * @param req    Yêu cầu sắp xếp lại thứ tự danh mục
     */
    void reorder(UUID userId, ReorderCategoriesRequest req);

    /**
     * Lấy danh sách các nhóm danh mục hệ thống.
     *
     * @return Danh sách nhóm danh mục
     */
    List<CategoryGroupResponse> listCategoryGroups();

    /**
     * Lấy toàn bộ danh sách ID danh mục con thuộc cây của danh mục chỉ định (gồm chính nó và các con).
     *
     * @param categoryId ID danh mục gốc
     * @return Danh sách ID danh mục thuộc cây
     */
    List<UUID> findCategoryTree(UUID categoryId);

    /**
     * Lấy bản đồ tham chiếu thông tin danh mục hiển thị cho người dùng.
     *
     * @param categoryIds Danh sách ID các danh mục cần tra cứu
     * @param userId      ID của người dùng
     * @return Bản đồ từ Category ID sang đối tượng tham chiếu danh mục
     */
    Map<UUID, CategoryRefResponse> findRefsVisibleToUser(Collection<UUID> categoryIds, UUID userId);

    /**
     * Tra cứu thông tin tham chiếu danh mục theo ID.
     *
     * @param categoryId ID danh mục
     * @return Thông tin tham chiếu danh mục (hoặc null nếu không tìm thấy)
     */
    CategoryRefResponse findRefById(UUID categoryId);

    /**
     * Tra cứu thông tin tham chiếu danh mục nếu danh mục đó hiển thị với người dùng.
     *
     * @param categoryId ID danh mục
     * @param userId     ID người dùng
     * @return Thông tin tham chiếu danh mục
     */
    CategoryRefResponse findRefVisibleToUser(UUID categoryId, UUID userId);

    /**
     * Tìm ID danh mục hệ thống dựa theo tên và loại.
     *
     * @param name Tên danh mục hệ thống
     * @param type Loại danh mục ({@code expense} hoặc {@code income})
     * @return ID của danh mục hệ thống
     */
    UUID findSystemCategoryId(String name, String type);

    /**
     * Tra cứu thông tin tham chiếu biểu tượng theo ID.
     *
     * @param iconId ID biểu tượng
     * @return Thông tin tham chiếu biểu tượng
     */
    IconRefResponse findIconRef(UUID iconId);

    /**
     * Truy vấn danh sách biểu tượng hệ thống có phân trang/tìm kiếm.
     *
     * @param iconGroup Nhóm biểu tượng cần lọc (tùy chọn)
     * @param search    Từ khóa tìm kiếm (tùy chọn)
     * @return Kết quả danh sách biểu tượng bọc trong wrapper
     */
    IconGroupResponseWrapper listIcons(String iconGroup, String search);

}

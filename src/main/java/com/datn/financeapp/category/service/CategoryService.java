package com.datn.financeapp.category.service;

import com.datn.financeapp.category.dto.request.CreateCategoryRequest;
import com.datn.financeapp.category.dto.request.ReorderCategoriesRequest;
import com.datn.financeapp.category.dto.request.UpdateCategoryRequest;
import com.datn.financeapp.category.dto.response.CategoryDetailResponse;
import com.datn.financeapp.category.dto.response.CategoryGroupResponse;
import com.datn.financeapp.category.dto.response.CategoryRefResponse;
import com.datn.financeapp.category.dto.response.CategoryResponse;
import com.datn.financeapp.category.dto.response.IconGroupResponse;
import com.datn.financeapp.category.dto.response.IconRefResponse;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Public API của module Category.
public interface CategoryService {

    record IconGroupResponseWrapper(List<IconGroupResponse> group, int total) {}

    List<CategoryResponse> list(UUID userId, String type, boolean asTree, boolean rootsOnly);

    /**
     * Cây danh mục hai cấp kèm cờ bật/tắt của một ví cụ thể (api/03 mục 9).
     *
     * <p>Ví không thuộc người dùng (và không nằm trong nhóm họ đang tham gia) trả 404, không phải
     * 403 — để không lộ việc ví đó có tồn tại hay không.
     */
    List<CategoryResponse> listForWallet(UUID userId, UUID walletId);

    /**
     * Bật/tắt một danh mục ở một ví. Tắt danh mục cha thì các con tắt theo.
     *
     * <p>Chỉ ảnh hưởng màn chọn danh mục: giao dịch cũ đã gán vào danh mục này không bị đụng tới,
     * báo cáo vẫn tính đủ.
     */
    void setCategoryEnabledForWallet(UUID userId, UUID walletId, UUID categoryId, boolean enabled);

    CategoryDetailResponse detail(UUID userId, UUID categoryId);

    CategoryResponse create(UUID userId, CreateCategoryRequest req);

    CategoryResponse update(UUID userId, UUID categoryId, UpdateCategoryRequest req);

    void delete(UUID userId, UUID categoryId, UUID replacementCategoryId);

    void reorder(UUID userId, ReorderCategoriesRequest req);

    List<CategoryGroupResponse> listCategoryGroups();

    List<UUID> findCategoryTree(UUID categoryId);

    Map<UUID, CategoryRefResponse> findRefsVisibleToUser(Collection<UUID> categoryIds, UUID userId);

    CategoryRefResponse findRefById(UUID categoryId);

    CategoryRefResponse findRefVisibleToUser(UUID categoryId, UUID userId);

    UUID findSystemCategoryId(String name, String type);

    IconRefResponse findIconRef(UUID iconId);

    IconGroupResponseWrapper listIcons(String iconGroup, String search);
}

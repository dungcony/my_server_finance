package com.datn.financeapp.category.controller;

import com.datn.financeapp.category.dto.response.CategoryDetailResponse;
import com.datn.financeapp.category.dto.response.CategoryGroupResponse;
import com.datn.financeapp.category.dto.response.CategoryResponse;
import com.datn.financeapp.category.dto.request.CreateCategoryRequest;
import com.datn.financeapp.category.dto.request.ReorderCategoriesRequest;
import com.datn.financeapp.category.dto.request.ToggleWalletCategoryRequest;
import com.datn.financeapp.category.dto.request.UpdateCategoryRequest;
import com.datn.financeapp.category.service.CategoryService;
import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.security.SecurityContextUtil;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 8 endpoint danh mục/nhóm lớn/kho icon (CAT-01..06, api/03-DANH-MUC.md mục 1-8).
 *
 * Chỉ {@code POST /categories} gắn {@link Idempotent} (D-11: chỉ POST-tạo-mới). PATCH/DELETE/
 * reorder KHÔNG gắn — cùng lý do như {@code WalletController}.
 */
@RestController
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping("/categories")
    public ApiResponse<List<CategoryResponse>> list(
            @RequestParam(required = false) String type,
            @RequestParam(name = "as_tree", required = false, defaultValue = "true") boolean asTree,
            @RequestParam(name = "roots_only", required = false, defaultValue = "false") boolean rootsOnly) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(categoryService.list(userId, type, asTree, rootsOnly));
    }

    @GetMapping("/categories/{id}")
    public ApiResponse<CategoryDetailResponse> detail(@PathVariable UUID id) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(categoryService.detail(userId, id));
    }

    @PostMapping("/categories")
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CategoryResponse> create(@Valid @RequestBody CreateCategoryRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(categoryService.create(userId, req));
    }

    @PatchMapping("/categories/{id}")
    public ApiResponse<CategoryResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateCategoryRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(categoryService.update(userId, id, req));
    }

    @DeleteMapping("/categories/{id}")
    public ApiResponse<Void> delete(
            @PathVariable UUID id,
            @RequestParam(name = "replacement_category_id", required = false) UUID replacementCategoryId) {
        UUID userId = SecurityContextUtil.currentUserId();
        categoryService.delete(userId, id, replacementCategoryId);
        return ApiResponse.of(null);
    }

    @PatchMapping("/categories/reorder")
    public ApiResponse<Void> reorder(@Valid @RequestBody ReorderCategoriesRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        categoryService.reorder(userId, req);
        return ApiResponse.of(null);
    }

    /**
     * Cây danh mục kèm cờ bật/tắt của một ví (api/03 mục 9).
     *
     * <p>Nằm ở {@code CategoryController} chứ không phải {@code WalletController} vì nghiệp vụ là
     * danh mục; ví chỉ là ngữ cảnh lọc.
     */
    @GetMapping("/wallets/{walletId}/categories")
    public ApiResponse<List<CategoryResponse>> listForWallet(@PathVariable UUID walletId) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(categoryService.listForWallet(userId, walletId));
    }

    @PatchMapping("/wallets/{walletId}/categories/{categoryId}")
    public ApiResponse<Void> setCategoryEnabledForWallet(
            @PathVariable UUID walletId,
            @PathVariable UUID categoryId,
            @Valid @RequestBody ToggleWalletCategoryRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        categoryService.setCategoryEnabledForWallet(userId, walletId, categoryId, req.isEnabled());
        return ApiResponse.of(null);
    }

    @GetMapping("/category-groups")
    public ApiResponse<List<CategoryGroupResponse>> categoryGroups() {
        return ApiResponse.of(categoryService.listCategoryGroups());
    }

    @GetMapping("/icons")
    public ApiResponse<CategoryService.IconGroupResponseWrapper> icons(
            @RequestParam(name = "icon_group", required = false) String iconGroup,
            @RequestParam(required = false) String search) {
        return ApiResponse.of(categoryService.listIcons(iconGroup, search));
    }
}

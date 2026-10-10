package com.datn.financeapp.module.category.controller;

import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.module.category.dto.request.CategoryCreateReq;
import com.datn.financeapp.module.category.dto.request.CategoryUpdateReq;
import com.datn.financeapp.module.category.dto.response.CategoryResponse;
import com.datn.financeapp.module.category.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller xử lý các yêu cầu HTTP liên quan đến cây danh mục thu chi. Đọc mở cho mọi người dùng
 * đã đăng nhập; ghi (tạo, sửa, xóa) chỉ dành cho người có quyền {@code categories:manage}.
 * <p>
 * <b>Các endpoint:</b>
 * <ul>
 *   <li>{@link #getMasters()}: {@code GET /categories} - Danh mục gốc</li>
 *   <li>{@link #getOfParent(String)}: {@code GET /categories/{code}/children} - Con trực tiếp của một danh mục</li>
 *   <li>{@link #create(CategoryCreateReq)}: {@code POST /categories} - Tạo danh mục</li>
 *   <li>{@link #update(String, CategoryUpdateReq)}: {@code PATCH /categories/{code}} - Sửa danh mục</li>
 *   <li>{@link #delete(String, String)}: {@code DELETE /categories/{code}} - Xóa danh mục cùng cả cây con</li>
 * </ul>
 * </p>
 *
 * @see CategoryService
 */
@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private static final String MANAGE =
            "hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).CATEGORIES_MANAGE.getValue())";

    private final CategoryService categoryService;

    @GetMapping
    public ApiResponse<List<CategoryResponse>> getMasters() {
        return ApiResponse.of(categoryService.getMasters());
    }

    @GetMapping("/{code}/children")
    public ApiResponse<List<CategoryResponse>> getOfParent(@PathVariable String code) {
        return ApiResponse.of(categoryService.getOfParent(code));
    }

    @PostMapping
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE)
    public ApiResponse<CategoryResponse> create(@Valid @RequestBody CategoryCreateReq req) {
        return ApiResponse.of(categoryService.create(req));
    }

    @PatchMapping("/{code}")
    @PreAuthorize(MANAGE)
    public ApiResponse<CategoryResponse> update(@PathVariable String code, @Valid @RequestBody CategoryUpdateReq req) {
        return ApiResponse.of(categoryService.update(code, req));
    }

    @DeleteMapping("/{code}")
    @PreAuthorize(MANAGE)
    public ApiResponse<Void> delete(@PathVariable String code) {
        categoryService.delete(code);
        return ApiResponse.of(null);
    }
}

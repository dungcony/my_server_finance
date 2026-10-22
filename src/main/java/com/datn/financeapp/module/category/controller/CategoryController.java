package com.datn.financeapp.module.category.controller;

import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.module.category.dto.request.CategoryCreateReq;
import com.datn.financeapp.module.category.dto.request.CategoryUpdateReq;
import com.datn.financeapp.module.category.dto.response.CategoryRes;
import com.datn.financeapp.module.category.service.CategoryBehavierService;
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
 * đã đăng nhập; ghi (tạo, sửa) yêu cầu quyền {@code categories:update}, xóa yêu cầu quyền {@code categories:delete}.
 *
 * @see CategoryService
 */
@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private static final String MANAGE = "hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).CATEGORIES_UPDATE.name())";

    private final CategoryBehavierService categoryBehavierService;

    @GetMapping
    public ApiResponse<List<CategoryRes>> getMasters() {
        return ApiResponse.of(categoryBehavierService.getMasters());
    }

    @GetMapping("/{code}/children")
    public ApiResponse<List<CategoryRes>> getOfParent(@PathVariable String code) {
        return ApiResponse.of(categoryBehavierService.getOfParent(code));
    }

    @PostMapping
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE)
    public ApiResponse<CategoryRes> create(@Valid @RequestBody CategoryCreateReq req) {
        return ApiResponse.of(categoryBehavierService.create(req));
    }

    @PatchMapping("/{code}")
    @PreAuthorize(MANAGE)
    public ApiResponse<CategoryRes> update(@PathVariable String code, @Valid @RequestBody CategoryUpdateReq req) {
        return ApiResponse.of(categoryBehavierService.update(code, req));
    }

    @DeleteMapping("/{code}")
    @PreAuthorize("hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).CATEGORIES_DELETE.name())")
    public ApiResponse<Void> delete(@PathVariable String code) {
        categoryBehavierService.delete(code);
        return ApiResponse.of(null);
    }
}

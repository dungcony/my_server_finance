package com.datn.financeapp.module.category.service;

import com.datn.financeapp.module.category.dto.response.CateSumaryRes;

import java.util.*;

/**
 * Service quản lý cây danh mục thu chi nhiều cấp do admin quản lý; người dùng thường chỉ đọc.
 */
public interface CategoryService {

    /**
     * kiểm tra và lấy thông tin cơ bản danh mục
     *
     * @param code           mã
     * @param includeDeleted false -> chỉ chưa xoá, true -> cả 2
     * @param isLeaf         true -> là lá
     */
    Optional<CateSumaryRes> findByCodeAndIsDeletedAndIsLeaf(String code, boolean includeDeleted, boolean isLeaf);

}

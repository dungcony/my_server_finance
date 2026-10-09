package com.datn.financeapp.module.category.repository;

import java.util.UUID;

/**
 * Projection tối thiểu (id, code) của bảng {@code categories}, dùng để nạp cache id ↔ code
 * mà không phải tải toàn bộ entity.
 */
public interface CategoryCodeView {

    UUID getId();

    String getCode();
}

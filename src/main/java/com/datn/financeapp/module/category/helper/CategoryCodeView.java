package com.datn.financeapp.module.category.helper;

import java.util.UUID;

/**
 * Cặp tối thiểu (id, code) của bảng {@code categories}, dùng để nạp {@link CateCache}
 * mà không phải tải toàn bộ entity.
 *
 * @param id   ID danh mục
 * @param code mã danh mục
 */
public record CategoryCodeView(UUID id, String code) {
}

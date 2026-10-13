package com.datn.financeapp.module.category.helper;

import java.util.UUID;

/**
 * Bộ ba tối thiểu (id, code, deleted) của bảng {@code categories}, dùng để nạp {@link CateCache}
 * mà không phải tải toàn bộ entity.
 *
 * @param id   ID danh mục
 * @param code mã danh mục
 * @param deleted {@code true} nếu danh mục đã xóa mềm
 */
public record CategoryCodeView(UUID id, String code, Boolean deleted) {
}

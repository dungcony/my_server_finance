package com.datn.financeapp.module.category.service;

import com.datn.financeapp.module.category.dto.response.IconGroupListResponse;
import com.datn.financeapp.module.category.entity.Icon;

/**
 * Service đọc kho biểu tượng hệ thống.
 * <p>
 * <b>Các hàm:</b> {@link #listIcons(String, String)}: lấy biểu tượng đang dùng, gom sẵn theo nhóm.
 * </p>
 */
public interface IconService {

    /**
     * Truy vấn biểu tượng đang dùng, có lọc theo nhóm và tìm kiếm, kết quả gom theo nhóm.
     *
     * @param iconGroup nhóm biểu tượng cần lọc (tùy chọn)
     * @param search    từ khóa tìm trong tên và từ khóa của biểu tượng (tùy chọn)
     * @return các nhóm biểu tượng kèm tổng số biểu tượng
     */
    IconGroupListResponse listIcons(String iconGroup, String search);

    Icon getActive(String code);
}

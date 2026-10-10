package com.datn.financeapp.module.category.dto.response;

import java.util.List;

/**
 * Một nhóm biểu tượng trong kết quả {@code GET /icons} (api/03-DANH-MUC.md mục 8).
 *
 * @param groupCode Mã nhóm (vd. {@code an_uong})
 * @param groupName Tên hiển thị của nhóm (vd. "Ăn uống")
 * @param items     Các biểu tượng thuộc nhóm
 */
public record IconGroupResponse(String groupCode, String groupName, List<IconResponse> items) {
}

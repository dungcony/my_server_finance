package com.datn.financeapp.module.icon.dto.response;

import java.util.List;

/**
 * Một nhóm biểu tượng trong kết quả {@code GET /icons}.
 *
 * @param groupCode Mã nhóm (vd. {@code an_uong})
 * @param groupName Tên hiển thị của nhóm (vd. "Ăn uống")
 * @param items     Các biểu tượng thuộc nhóm
 */
public record IconGroupResponse(String groupCode, String groupName, List<IconResponse> items) {
}

package com.datn.financeapp.module.category.dto.response;

import java.util.List;

/**
 * Kết quả {@code GET /icons}: biểu tượng đã gom sẵn theo nhóm kèm tổng số.
 *
 * @param group Danh sách nhóm biểu tượng
 * @param total Tổng số biểu tượng trong mọi nhóm
 */
public record IconGroupListResponse(List<IconGroupResponse> group, int total) {
}

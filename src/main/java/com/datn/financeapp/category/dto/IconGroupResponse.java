package com.datn.financeapp.category.dto;

import java.util.List;

/** Một nhóm biểu tượng trong GET /icons (api/03-DANH-MUC.md mục 8). */
public record IconGroupResponse(String groupCode, String groupName, List<IconResponse> items) {}

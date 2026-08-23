package com.datn.financeapp.category.dto;

import java.util.UUID;

/** GET /category-groups (api/03-DANH-MUC.md mục 7). */
public record CategoryGroupResponse(UUID id, String name, String color, CategoryResponse.IconSummary icon, int sortOrder) {}

package com.datn.financeapp.category.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

/** Body của PATCH /categories/reorder (api/03-DANH-MUC.md mục 6). */
public record ReorderCategoriesRequest(UUID parentCategoryId, @NotEmpty List<UUID> sortOrder) {}

package com.datn.financeapp.category.dto.response;

import java.util.UUID;

/** Phần tử trong {@code items} của IconGroupResponse (api/03-DANH-MUC.md mục 8). */
public record IconResponse(UUID id, String code, String displayName, String pathData) {}

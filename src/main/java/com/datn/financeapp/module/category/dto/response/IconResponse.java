package com.datn.financeapp.module.category.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.UUID;

// Phần tử trong {@code items} của IconGroupResponse (api/03-DANH-MUC.md mục 8).
public record IconResponse(
        @JsonIgnore UUID id,
        String code,
        String displayName,
        String pathData) {
}

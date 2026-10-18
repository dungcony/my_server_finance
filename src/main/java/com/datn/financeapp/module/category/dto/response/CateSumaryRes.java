package com.datn.financeapp.module.category.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.UUID;

public record CateSumaryRes(
        @JsonIgnore UUID id,
        String name,
        String code,
        String icon,
        String color
) {
}

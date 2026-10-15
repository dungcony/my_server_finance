package com.datn.financeapp.module.category.dto.response;

import com.datn.financeapp.module.icon.dto.response.IconResponse;

public record CateSumaryRes(
        String name,
        String code,
        IconResponse icon
) {
}

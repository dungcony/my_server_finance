package com.datn.financeapp.module.category.dto.request;

import java.util.UUID;

/**
 * Update cate
 *
 * @param name
 * @param description
 * @param iconId
 * @param color
 * @param parentCode  khi muốn thay đổi cha
 */
public record CategoryUpdateReq(
        String name,
        String description,
        UUID iconId,
        String color,
        String parentCode
) {
}

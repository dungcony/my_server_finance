package com.datn.financeapp.module.budget.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Trạng thái chi tiêu của một ngân sách theo tỉ lệ đã chi trên hạn mức:
 * NORMAL, NEAR_LIMIT, OVER_LIMIT.
 */
public enum BudgetStatus {
    NORMAL,
    NEAR_LIMIT,
    OVER_LIMIT;

    @JsonCreator
    public static BudgetStatus fromString(String value) {
        if (value == null) {
            return null;
        }
        for (BudgetStatus status : values()) {
            if (status.name().equalsIgnoreCase(value.trim())
                    || status.name().replace("_", "").equalsIgnoreCase(value.replace("_", "").trim())) {
                return status;
            }
        }
        return NORMAL;
    }

    public String code() {
        return name().toLowerCase();
    }

    /**
     * Đã từ 80% hạn mức trở lên (sắp chạm hoặc đã vượt) — đáng cảnh báo.
     */
    public boolean needsAttention() {
        return this != NORMAL;
    }
}

package com.datn.financeapp.module.budget.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Trạng thái chi tiêu của một ngân sách theo tỉ lệ đã chi trên hạn mức. Trên JSON là chuỗi chữ thường
 * ({@code normal}, {@code near_limit}, {@code over_limit}).
 */
public enum BudgetStatus {
    NORMAL("normal"),
    NEAR_LIMIT("near_limit"),
    OVER_LIMIT("over_limit");

    private final String code;

    BudgetStatus(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    /**
     * Đã từ 80% hạn mức trở lên (sắp chạm hoặc đã vượt) — đáng cảnh báo.
     */
    public boolean needsAttention() {
        return this != NORMAL;
    }
}

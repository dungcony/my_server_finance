package com.datn.financeapp.module.budget.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BudgetStatusTest {

    @Test
    @DisplayName("Mã trên JSON là chuỗi chữ thường đúng hợp đồng cũ")
    void codes() {
        assertThat(BudgetStatus.NORMAL.code()).isEqualTo("normal");
        assertThat(BudgetStatus.NEAR_LIMIT.code()).isEqualTo("near_limit");
        assertThat(BudgetStatus.OVER_LIMIT.code()).isEqualTo("over_limit");
    }

    @Test
    @DisplayName("Chỉ trạng thái bình thường là không cần chú ý")
    void needsAttention() {
        assertThat(BudgetStatus.NORMAL.needsAttention()).isFalse();
        assertThat(BudgetStatus.NEAR_LIMIT.needsAttention()).isTrue();
        assertThat(BudgetStatus.OVER_LIMIT.needsAttention()).isTrue();
    }
}

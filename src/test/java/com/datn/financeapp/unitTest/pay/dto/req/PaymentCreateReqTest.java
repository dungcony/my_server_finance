package com.datn.financeapp.unitTest.pay.dto.req;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datn.financeapp.module.pay.dto.req.PaymentCreateReq;
import com.datn.financeapp.module.pay.enums.PaymentType;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaymentCreateReqTest {

    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("đầu vào hợp lệ -> tạo thành công")
    void valid() {
        PaymentCreateReq req = new PaymentCreateReq(userId, PaymentType.PREMIUM_UPGRADE, "PREMIUM_1M", 49_000L);
        assertThat(req.userId()).isEqualTo(userId);
        assertThat(req.type()).isEqualTo(PaymentType.PREMIUM_UPGRADE);
        assertThat(req.referenceId()).isEqualTo("PREMIUM_1M");
        assertThat(req.amount()).isEqualTo(49_000L);
    }

    @Test
    @DisplayName("userId null -> ném IllegalArgumentException")
    void nullUserId() {
        assertThatThrownBy(() -> new PaymentCreateReq(null, PaymentType.PREMIUM_UPGRADE, "PREMIUM_1M", 49_000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
    }

    @Test
    @DisplayName("type null -> ném IllegalArgumentException")
    void nullType() {
        assertThatThrownBy(() -> new PaymentCreateReq(userId, null, "PREMIUM_1M", 49_000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type");
    }

    @Test
    @DisplayName("referenceId null hoặc blank -> ném IllegalArgumentException")
    void invalidReferenceId() {
        assertThatThrownBy(() -> new PaymentCreateReq(userId, PaymentType.PREMIUM_UPGRADE, null, 49_000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("referenceId");

        assertThatThrownBy(() -> new PaymentCreateReq(userId, PaymentType.PREMIUM_UPGRADE, "   ", 49_000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("referenceId");
    }

    @Test
    @DisplayName("amount <= 0 -> ném IllegalArgumentException")
    void invalidAmount() {
        assertThatThrownBy(() -> new PaymentCreateReq(userId, PaymentType.PREMIUM_UPGRADE, "PREMIUM_1M", 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");

        assertThatThrownBy(() -> new PaymentCreateReq(userId, PaymentType.PREMIUM_UPGRADE, "PREMIUM_1M", -100L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }
}

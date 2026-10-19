package com.datn.financeapp.unitTest.user.event.Listener;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.datn.financeapp.module.pay.enums.PaymentType;
import com.datn.financeapp.module.pay.events.publisher.PaymentPaidEvent;
import com.datn.financeapp.module.user.event.Listener.PremiumPaymentListener;
import com.datn.financeapp.module.user.exception.UserNotFoundException;
import com.datn.financeapp.module.user.service.UserService;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PremiumPaymentListenerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private PremiumPaymentListener listener;

    @Test
    @DisplayName("PREMIUM_6M -> nâng cấp đúng 6 tháng cho đúng user")
    void onPaymentPaid_SixMonthPlan_UpgradesSixMonths() {
        UUID userId = UUID.randomUUID();

        listener.onPaymentPaid(new PaymentPaidEvent(userId, PaymentType.PREMIUM_UPGRADE, "PREMIUM_6M", 100_001L));

        verify(userService).upgradeToPremium(userId, 6);
    }

    @Test
    @DisplayName("PREMIUM_12M -> nâng cấp 12 tháng")
    void onPaymentPaid_TwelveMonthPlan_UpgradesTwelveMonths() {
        UUID userId = UUID.randomUUID();

        listener.onPaymentPaid(new PaymentPaidEvent(userId, PaymentType.PREMIUM_UPGRADE, "PREMIUM_12M", 100_002L));

        verify(userService).upgradeToPremium(userId, 12);
    }

    @Test
    @DisplayName("referenceId không phải gói hợp lệ hoặc rỗng -> ném IllegalStateException có kèm log lỗi")
    void onPaymentPaid_UnknownPlan_Throws() {
        PaymentPaidEvent event = new PaymentPaidEvent(UUID.randomUUID(), PaymentType.PREMIUM_UPGRADE, "PREMIUM_99M", 100_003L);

        assertThatThrownBy(() -> listener.onPaymentPaid(event)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(userService);

        PaymentPaidEvent nullRefEvent = new PaymentPaidEvent(UUID.randomUUID(), PaymentType.PREMIUM_UPGRADE, null, 100_004L);
        assertThatThrownBy(() -> listener.onPaymentPaid(nullRefEvent)).isInstanceOf(IllegalStateException.class);

        PaymentPaidEvent blankRefEvent = new PaymentPaidEvent(UUID.randomUUID(), PaymentType.PREMIUM_UPGRADE, "   ", 100_005L);
        assertThatThrownBy(() -> listener.onPaymentPaid(blankRefEvent)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("user đã bị xoá khi đang nâng cấp -> log lỗi [PAYMENT-RECONCILE], không ném lỗi ra ngoài")
    void onPaymentPaid_UserNotFound_DoesNotThrow() {
        UUID userId = UUID.randomUUID();
        doThrow(new UserNotFoundException()).when(userService).upgradeToPremium(userId, 1);

        PaymentPaidEvent event = new PaymentPaidEvent(userId, PaymentType.PREMIUM_UPGRADE, "PREMIUM_1M", 100_006L);

        assertThatCode(() -> listener.onPaymentPaid(event)).doesNotThrowAnyException();
        verify(userService).upgradeToPremium(userId, 1);
    }
}

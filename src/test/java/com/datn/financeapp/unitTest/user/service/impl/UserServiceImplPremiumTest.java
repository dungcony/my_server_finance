package com.datn.financeapp.unitTest.user.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.module.pay.dto.req.PaymentCreateReq;
import com.datn.financeapp.module.pay.dto.res.PaymentCreateRes;
import com.datn.financeapp.module.pay.enums.PaymentStatus;
import com.datn.financeapp.module.pay.enums.PaymentType;
import com.datn.financeapp.module.pay.service.PaymentService;
import com.datn.financeapp.module.user.config.PremiumPlanProperties;
import com.datn.financeapp.module.user.entity.User;
import com.datn.financeapp.module.user.enums.PremiumPlan;
import com.datn.financeapp.module.user.enums.UserPlan;
import com.datn.financeapp.module.user.exception.UserNotFoundException;
import com.datn.financeapp.module.user.helper.RolePermissionCacheHelper;
import com.datn.financeapp.module.user.mapper.UserMapper;
import com.datn.financeapp.module.user.repository.UserRepository;
import com.datn.financeapp.module.user.repository.UserRoleRepository;
import com.datn.financeapp.module.user.service.impl.UserServiceImpl;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceImplPremiumTest {

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RolePermissionCacheHelper rolePermissionCacheHelper;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PaymentService paymentService;

    private UserServiceImpl userService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        PremiumPlanProperties prices = new PremiumPlanProperties(
                new PremiumPlanProperties.Prices(49_000, 249_000, 449_000));
        userService = new UserServiceImpl(passwordEncoder, userRepository, rolePermissionCacheHelper,
                userRoleRepository, eventPublisher, userMapper, paymentService, prices, Clock.fixed(Instant.parse("2026-04-20T10:00:00Z"), ZoneOffset.UTC));
        userId = UUID.randomUUID();
    }

    private User user(UserPlan plan, Instant planExpiresAt) {
        return User.builder().id(userId).email("a@example.com").plan(plan).planExpiresAt(planExpiresAt).build();
    }

    private void stubUser(User user) {
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
    }

    private Instant plusMonthsUtc(Instant base, int months) {
        return base.atZone(ZoneOffset.UTC).plusMonths(months).toInstant();
    }

    // ----------------------------- createPremiumUpgrade -----------------------------

    @Test
    @DisplayName("createPremiumUpgrade: user FREE -> tạo đơn với giá do server tính theo gói")
    void createPremiumUpgrade_Free_UsesServerPrice() {
        stubUser(user(UserPlan.FREE, null));
        PaymentCreateRes created = new PaymentCreateRes(100_001L, "PREMIUM_6M", 249_000, PaymentStatus.PENDING, "url", Instant.now());
        when(paymentService.createPayment(any(PaymentCreateReq.class))).thenReturn(created);

        PaymentCreateRes res = userService.createPremiumUpgrade(userId, PremiumPlan.PREMIUM_6M);

        assertThat(res).isSameAs(created);
        ArgumentCaptor<PaymentCreateReq> req = ArgumentCaptor.forClass(PaymentCreateReq.class);
        verify(paymentService).createPayment(req.capture());
        assertThat(req.getValue().userId()).isEqualTo(userId);
        assertThat(req.getValue().type()).isEqualTo(PaymentType.PREMIUM_UPGRADE);
        assertThat(req.getValue().referenceId()).isEqualTo("PREMIUM_6M");
        assertThat(req.getValue().amount()).isEqualTo(249_000);
    }

    @Test
    @DisplayName("createPremiumUpgrade: đang premium còn hạn -> PREMIUM_ALREADY_ACTIVE, không tạo đơn")
    void createPremiumUpgrade_ActivePremium_Rejected() {
        stubUser(user(UserPlan.PREMIUM, Instant.now().plusSeconds(86_400)));

        assertThatThrownBy(() -> userService.createPremiumUpgrade(userId, PremiumPlan.PREMIUM_1M))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("PREMIUM_ALREADY_ACTIVE");
        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("createPremiumUpgrade: premium không thời hạn (admin seed) -> PREMIUM_ALREADY_ACTIVE")
    void createPremiumUpgrade_PremiumWithoutExpiry_Rejected() {
        stubUser(user(UserPlan.PREMIUM, null));

        assertThatThrownBy(() -> userService.createPremiumUpgrade(userId, PremiumPlan.PREMIUM_1M))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("PREMIUM_ALREADY_ACTIVE");
        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("createPremiumUpgrade: premium đã hết hạn (job chưa chạy) -> vẫn được mua")
    void createPremiumUpgrade_ExpiredPremium_Allowed() {
        stubUser(user(UserPlan.PREMIUM, Instant.now().minusSeconds(60)));
        when(paymentService.createPayment(any(PaymentCreateReq.class)))
                .thenReturn(new PaymentCreateRes(1L, "PREMIUM_1M", 49_000, PaymentStatus.PENDING, "url", Instant.now()));

        userService.createPremiumUpgrade(userId, PremiumPlan.PREMIUM_1M);

        verify(paymentService).createPayment(any(PaymentCreateReq.class));
    }

    @Test
    @DisplayName("createPremiumUpgrade: không có user -> UserNotFoundException")
    void createPremiumUpgrade_UserMissing_Throws() {
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.createPremiumUpgrade(userId, PremiumPlan.PREMIUM_1M))
                .isInstanceOf(UserNotFoundException.class);
        verifyNoInteractions(paymentService);
    }

    // ------------------------------- upgradeToPremium -------------------------------

    @Test
    @DisplayName("upgradeToPremium: FREE -> PREMIUM, hạn = bây giờ + số tháng đã mua")
    void upgradeToPremium_Free_SetsPlanAndExpiryFromNow() {
        User user = user(UserPlan.FREE, null);
        stubUser(user);

        Instant before = Instant.now();
        userService.upgradeToPremium(userId, 6);
        Instant after = Instant.now();

        assertThat(user.getPlan()).isEqualTo(UserPlan.PREMIUM);
        assertThat(user.getPlanExpiresAt()).isBetween(plusMonthsUtc(before, 6), plusMonthsUtc(after, 6));
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("upgradeToPremium: premium đã hết hạn -> tính từ bây giờ, không tính từ hạn cũ")
    void upgradeToPremium_ExpiredPremium_CountsFromNow() {
        User user = user(UserPlan.PREMIUM, Instant.now().minusSeconds(86_400L * 30));
        stubUser(user);

        Instant before = Instant.now();
        userService.upgradeToPremium(userId, 1);
        Instant after = Instant.now();

        assertThat(user.getPlan()).isEqualTo(UserPlan.PREMIUM);
        assertThat(user.getPlanExpiresAt()).isBetween(plusMonthsUtc(before, 1), plusMonthsUtc(after, 1));
    }

    @Test
    @DisplayName("upgradeToPremium: còn hạn mà vẫn nhận được tiền -> cộng dồn từ hạn hiện tại, không mất tiền của user")
    void upgradeToPremium_ActivePremium_StacksFromCurrentExpiry() {
        Instant currentExpiry = Instant.now().plusSeconds(86_400L * 10);
        User user = user(UserPlan.PREMIUM, currentExpiry);
        stubUser(user);

        userService.upgradeToPremium(userId, 6);

        assertThat(user.getPlanExpiresAt()).isEqualTo(plusMonthsUtc(currentExpiry, 6));
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("upgradeToPremium: premium không thời hạn -> giữ nguyên, không ghi gì")
    void upgradeToPremium_PremiumWithoutExpiry_NoChange() {
        User user = user(UserPlan.PREMIUM, null);
        stubUser(user);

        userService.upgradeToPremium(userId, 1);

        assertThat(user.getPlanExpiresAt()).isNull();
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("upgradeToPremium: không có user -> UserNotFoundException (làm rollback webhook để payOS gửi lại)")
    void upgradeToPremium_UserMissing_Throws() {
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.upgradeToPremium(userId, 1)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("upgradeToPremium: months <= 0 -> ném IllegalArgumentException")
    void upgradeToPremium_InvalidMonths_Throws() {
        assertThatThrownBy(() -> userService.upgradeToPremium(userId, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("months");

        assertThatThrownBy(() -> userService.upgradeToPremium(userId, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("months");
    }

    // --------------------------- downgradeExpiredPremium ---------------------------

    @Test
    @DisplayName("downgradeExpiredPremium: gọi query hạ PREMIUM -> FREE với mốc hiện tại, trả số tài khoản bị hạ")
    void downgradeExpiredPremium_DelegatesToRepository() {
        when(userRepository.downgradeExpiredPremium(any(), any(), any())).thenReturn(3);

        Instant before = Instant.now();
        int count = userService.downgradeExpiredPremium();
        Instant after = Instant.now();

        assertThat(count).isEqualTo(3);
        ArgumentCaptor<Instant> now = ArgumentCaptor.forClass(Instant.class);
        verify(userRepository).downgradeExpiredPremium(
                eq(UserPlan.FREE),
                eq(UserPlan.PREMIUM),
                now.capture());
        assertThat(now.getValue()).isBetween(before, after);
    }
}

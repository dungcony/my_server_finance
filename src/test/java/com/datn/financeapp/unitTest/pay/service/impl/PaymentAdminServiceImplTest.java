package com.datn.financeapp.unitTest.pay.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datn.financeapp.module.pay.dto.res.PaymentAdminListRes;
import com.datn.financeapp.module.pay.dto.res.PaymentAdminRes;
import com.datn.financeapp.module.pay.entity.Payment;
import com.datn.financeapp.module.pay.enums.PaymentStatus;
import com.datn.financeapp.module.pay.enums.PaymentType;
import com.datn.financeapp.module.pay.exceptions.PaymentNotFoundException;
import com.datn.financeapp.module.pay.mapper.PaymentMapperImpl;
import com.datn.financeapp.module.pay.repository.PaymentRepository;
import com.datn.financeapp.module.pay.service.impl.PaymentAdminServiceImpl;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class PaymentAdminServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;

    private PaymentAdminServiceImpl paymentAdminService;

    @BeforeEach
    void setUp() {
        paymentAdminService = new PaymentAdminServiceImpl(paymentRepository, new PaymentMapperImpl());
    }

    private Payment payment() {
        return Payment.builder()
                .id(UUID.randomUUID())
                .orderCode(100_001L)
                .userId(UUID.randomUUID())
                .type(PaymentType.PREMIUM_UPGRADE)
                .referenceId("PREMIUM_1M")
                .amount(49_000L)
                .status(PaymentStatus.PAID)
                .payosLinkId("link")
                .expiresAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("list: map đủ trường quản trị (user_id, payos_link_id) và meta phân trang")
    @SuppressWarnings("unchecked")
    void list_MapsAdminFieldsAndMeta() {
        Payment payment = payment();
        when(paymentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(payment), PageRequest.of(0, 20), 1));

        PaymentAdminListRes res = paymentAdminService.list(
                null, PaymentStatus.PAID, null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                PageRequest.of(0, 20));

        assertThat(res.items()).hasSize(1);
        assertThat(res.items().get(0).userId()).isEqualTo(payment.getUserId());
        assertThat(res.items().get(0).payosLinkId()).isEqualTo("link");
        assertThat(res.meta().totalItems()).isEqualTo(1);
    }

    @Test
    @DisplayName("list: luôn sắp xếp mới nhất trước, bỏ qua sort client gửi lên")
    @SuppressWarnings("unchecked")
    void list_AlwaysNewestFirst() {
        when(paymentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        paymentAdminService.list(null, null, null, null, null,
                PageRequest.of(2, 10, Sort.by("amount")));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(paymentRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(pageable.getValue().getSort().getOrderFor("amount")).isNull();
    }

    @Test
    @DisplayName("detail: đơn bất kỳ theo order_code, không kiểm tra chủ sở hữu")
    void detail_Found() {
        Payment payment = payment();
        when(paymentRepository.findByOrderCode(100_001L)).thenReturn(Optional.of(payment));

        PaymentAdminRes res = paymentAdminService.detail(100_001L);

        assertThat(res.orderCode()).isEqualTo(100_001L);
        assertThat(res.userId()).isEqualTo(payment.getUserId());
    }

    @Test
    @DisplayName("detail: không có đơn -> PaymentNotFoundException")
    void detail_NotFound() {
        when(paymentRepository.findByOrderCode(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentAdminService.detail(1L)).isInstanceOf(PaymentNotFoundException.class);
    }
}

package com.datn.financeapp.module.pay.service.impl;

import com.datn.financeapp.common.response.PageMeta;
import com.datn.financeapp.module.pay.dto.res.PaymentAdminListRes;
import com.datn.financeapp.module.pay.dto.res.PaymentAdminRes;
import com.datn.financeapp.module.pay.entity.Payment;
import com.datn.financeapp.module.pay.enums.PaymentStatus;
import com.datn.financeapp.module.pay.enums.PaymentType;
import com.datn.financeapp.module.pay.exceptions.PaymentNotFoundException;
import com.datn.financeapp.module.pay.helper.PaymentSpecs;
import com.datn.financeapp.module.pay.mapper.PaymentMapper;
import com.datn.financeapp.module.pay.repository.PaymentRepository;
import com.datn.financeapp.module.pay.service.PaymentAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentAdminServiceImpl implements PaymentAdminService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;

    @Override
    public PaymentAdminListRes list(UUID userId, PaymentStatus status, PaymentType type,
                                    LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        Instant createdFrom = fromDate == null ? null : fromDate.atStartOfDay(VN_ZONE).toInstant();
        Instant createdTo = toDate == null ? null : toDate.plusDays(1).atStartOfDay(VN_ZONE).toInstant();

        Page<Payment> page = paymentRepository.findAll(
                PaymentSpecs.filter(userId, status, type, createdFrom, createdTo),
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "createdAt")));

        return new PaymentAdminListRes(page.map(paymentMapper::toAdminRes).getContent(), PageMeta.from(page));
    }

    @Override
    public PaymentAdminRes detail(long orderCode) {
        return paymentRepository.findByOrderCode(orderCode)
                .map(paymentMapper::toAdminRes)
                .orElseThrow(PaymentNotFoundException::new);
    }
}

package com.datn.financeapp.module.pay.service;

import com.datn.financeapp.module.pay.dto.res.PaymentAdminListRes;
import com.datn.financeapp.module.pay.dto.res.PaymentAdminRes;
import com.datn.financeapp.module.pay.enums.PaymentStatus;
import com.datn.financeapp.module.pay.enums.PaymentType;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Tra cứu thanh toán toàn hệ thống cho quản trị viên (chỉ đọc).
 */
public interface PaymentAdminService {

    PaymentAdminListRes list(UUID userId, PaymentStatus status, PaymentType type,
                             LocalDate fromDate, LocalDate toDate, Pageable pageable);

    PaymentAdminRes detail(long orderCode);
}

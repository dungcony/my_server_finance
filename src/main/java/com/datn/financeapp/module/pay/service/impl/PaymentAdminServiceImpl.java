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

/**
 * Xử lý nghiệp vụ tra cứu danh sách và chi tiết thanh toán cho quản trị viên.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentAdminServiceImpl implements PaymentAdminService {

    // múi giờ Việt Nam để chuyển đổi khoảng ngày tìm kiếm từ giao diện
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;

    // truy vấn danh sách phân trang các giao dịch thanh toán toàn hệ thống kèm bộ lọc
    @Override
    public PaymentAdminListRes list(UUID userId, PaymentStatus status, PaymentType type,
                                    LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        // tính toán mốc thời gian bắt đầu (đầu ngày theo giờ Việt Nam)
        Instant createdFrom = fromDate == null ? null : fromDate.atStartOfDay(VN_ZONE).toInstant();

        // tính toán mốc thời gian kết thúc (đầu ngày kế tiếp để bao trọn ngày toDate)
        Instant createdTo = toDate == null ? null : toDate.plusDays(1).atStartOfDay(VN_ZONE).toInstant();

        // tìm kiếm theo đặc tả Specification kết hợp sắp xếp mới nhất lên đầu
        Page<Payment> page = paymentRepository.findAll(
                PaymentSpecs.filter(userId, status, type, createdFrom, createdTo),
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "createdAt")));

        return new PaymentAdminListRes(page.map(paymentMapper::toAdminRes).getContent(), PageMeta.from(page));
    }

    // tra cứu chi tiết một đơn thanh toán theo mã đơn hàng
    @Override
    public PaymentAdminRes detail(long orderCode) {
        return paymentRepository.findByOrderCode(orderCode)
                .map(paymentMapper::toAdminRes)
                .orElseThrow(PaymentNotFoundException::new);
    }
}

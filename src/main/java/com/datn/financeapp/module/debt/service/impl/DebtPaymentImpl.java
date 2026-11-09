package com.datn.financeapp.module.debt.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.response.PageMeta;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.debt.dto.request.DebtPaymentCreateReq;
import com.datn.financeapp.module.debt.dto.request.DebtPaymentFilterReq;
import com.datn.financeapp.module.debt.dto.request.DebtPaymentUpdateReq;
import com.datn.financeapp.module.debt.dto.response.DebtPaymentListRes;
import com.datn.financeapp.module.debt.dto.response.DebtPaymentRes;
import com.datn.financeapp.module.debt.entity.Debt;
import com.datn.financeapp.module.debt.entity.DebtPayment;
import com.datn.financeapp.module.debt.enums.DebtStatus;
import com.datn.financeapp.module.debt.events.DebtCompletedEvent;
import com.datn.financeapp.module.debt.helper.DebtHelper;
import com.datn.financeapp.module.debt.helper.DebtPaymentSpec;
import com.datn.financeapp.module.debt.mapper.DebtPaymentMapper;
import com.datn.financeapp.module.debt.repository.DebtPaymentRepository;
import com.datn.financeapp.module.debt.service.DebtPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DebtPaymentImpl implements DebtPaymentService {

    private final DebtPaymentRepository debtPaymentRepository;
    private final DebtPaymentMapper debtPaymentMapper;
    private final DebtHelper debtHelper;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    @Override
    public DebtPaymentRes create(UUID id, DebtPaymentCreateReq req, boolean isAdmin) {
        // Lấy thông tin khoản nợ cha
        var debt = debtHelper.findDebt(id, isAdmin);

        if (debt.getStatus() != DebtStatus.INPROCESS) {
            throw new BusinessException(ErrorCode.DEBT_IS_COMPLETED);
        }

        long remaining = debt.getTotalAmount() - debt.getPaidAmount();
        if (req.amount() > remaining) {
            throw new BusinessException(ErrorCode.CONFLICT, "Số tiền thanh toán vượt quá số nợ còn lại (" + remaining + " đ).");
        }

        // Khởi tạo payment (giao dịch độc lập, không có transactionId)
        DebtPayment dp = DebtPayment.builder()
                .id(UUID.randomUUID())
                .debtId(id)
                .amount(req.amount())
                .paidDate(req.paidDate())
                .note(req.note())
                .build();

        debtPaymentRepository.save(dp);

        // Kích hoạt event nếu khoản nợ hoàn thành
        if (debt.getTotalAmount() <= debt.getPaidAmount() + dp.getAmount()) {
            applicationEventPublisher.publishEvent(new DebtCompletedEvent(
                    debt.getId(),
                    debt.getUserId(),
                    debt.getName(),
                    debt.getTotalAmount(),
                    debt.getType()
            ));
        }

        return debtPaymentMapper.toRes(dp);
    }

    @Override
    @Transactional(readOnly = true)
    public DebtPaymentListRes gets(UUID debtId, DebtPaymentFilterReq req, Pageable pageable) {
        Page<DebtPayment> page = debtPaymentRepository.findAll(DebtPaymentSpec.filter(debtId, req), pageable);
        return debtPaymentMapper.toDebtPaymentListRes(page.getContent(), PageMeta.from(page));
    }

    @Transactional
    @Override
    public DebtPaymentRes update(UUID id, DebtPaymentUpdateReq req, boolean isAdmin) {
        DebtPayment dp = debtPaymentRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy đợt thanh toán"));

        // Lấy khoản nợ cha để kiểm tra quyền và tổng tiền nợ
        Debt debt = debtHelper.findDebt(dp.getDebtId(), isAdmin);

        long oldAmount = dp.getAmount();
        if (req.amount() != null) {
            long remainingWithoutThis = debt.getTotalAmount() - (debt.getPaidAmount() - oldAmount);
            if (req.amount() > remainingWithoutThis) {
                throw new BusinessException(ErrorCode.CONFLICT, "Số tiền thanh toán mới vượt quá số nợ còn lại.");
            }
            dp.setAmount(req.amount());
        }
        if (req.paidDate() != null) {
            dp.setPaidDate(req.paidDate());
        }
        if (req.note() != null) {
            dp.setNote(req.note());
        }

        debtPaymentRepository.save(dp);
        // Trigger DB sẽ tự động: cập nhật debts.paid_amount, debts.status và cộng/trừ chênh lệch ví

        long newPaidAmount = debt.getPaidAmount() - oldAmount + dp.getAmount();
        if (debt.getStatus() != DebtStatus.COMPLETED && debt.getTotalAmount() <= newPaidAmount) {
            applicationEventPublisher.publishEvent(new DebtCompletedEvent(
                    debt.getId(),
                    debt.getUserId(),
                    debt.getName(),
                    debt.getTotalAmount(),
                    debt.getType()
            ));
        }

        return debtPaymentMapper.toRes(dp);
    }

    @Transactional
    @Override
    public void delete(UUID id, boolean isAdmin) {
        DebtPayment dp = debtPaymentRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy đợt thanh toán"));

        if (!isAdmin) {
            // Kiểm tra quyền qua khoản nợ cha
            debtHelper.validDebt(dp.getDebtId(), SecurityContextUtil.currentUserId());
        }

        // Soft-delete
        dp.setIsDeleted(true);
        debtPaymentRepository.save(dp);
        // Trigger DB sẽ tự động: trừ lại debts.paid_amount, mở lại trạng thái INPROCESS nếu cần, và hoàn tác tiền ví!
    }
}

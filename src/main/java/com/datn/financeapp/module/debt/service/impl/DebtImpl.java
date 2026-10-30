package com.datn.financeapp.module.debt.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.response.PageMeta;
import com.datn.financeapp.module.debt.dto.request.DebtCreateReq;
import com.datn.financeapp.module.debt.dto.request.DebtFilterReq;
import com.datn.financeapp.module.debt.dto.request.DebtUpdateReq;
import com.datn.financeapp.module.debt.dto.response.DebtListRes;
import com.datn.financeapp.module.debt.dto.response.DebtRes;
import com.datn.financeapp.module.debt.entity.Debt;
import com.datn.financeapp.module.debt.enums.DebtStatus;
import com.datn.financeapp.module.debt.events.DebtCompletedEvent;
import com.datn.financeapp.module.debt.events.DebtDueReminderEvent;
import com.datn.financeapp.module.debt.helper.DebtHelper;
import com.datn.financeapp.module.debt.helper.DebtSpec;
import com.datn.financeapp.module.debt.mapper.DebtMapper;
import com.datn.financeapp.module.debt.repository.DebtRepository;
import com.datn.financeapp.module.debt.service.DebtService;
import com.datn.financeapp.module.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DebtImpl implements DebtService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final DebtRepository debtRepository;
    private final WalletService walletService;
    private final DebtMapper debtMapper;
    private final DebtHelper debtHelper;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    @Override
    public DebtRes create(UUID userId, DebtCreateReq req) {
        LocalDate issuedDate = req.issuedDate() != null ? req.issuedDate() : LocalDate.now();

        if (req.dueDate() != null && req.dueDate().isBefore(issuedDate))
            throw new BusinessException(ErrorCode.DEBT_DUE_DATE_INVALID);

        // kiểm tra tính đúng đắn của ví
        walletService.findSumaryByIdAndUser(req.walletId(), userId, false)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy ví hợp lệ."));

        Debt debt = debtMapper.toEntity(req, req.walletId());
        debt.setId(UUID.randomUUID());
        debt.setUserId(userId);
        debt.setIssuedDate(issuedDate);
        debt.setPaidAmount(0L);

        debtRepository.save(debt);

        return debtMapper.toRes(debt);
    }

    @Override
    @Transactional(readOnly = true)
    public DebtRes get(UUID id) {
        return debtMapper.toRes(debtHelper.findDebt(id, false));
    }

    @Override
    @Transactional(readOnly = true)
    public DebtListRes gets(UUID userId, DebtFilterReq req, Pageable pageable) {

        Page<Debt> page = debtRepository.findAll(DebtSpec.filter(userId, req), pageable);

        return debtMapper.toDebtListRes(page.getContent(), PageMeta.from(page));
    }

    @Override
    @Transactional
    public DebtRes update(UUID id, DebtUpdateReq req, boolean isAdmin) {
        Debt debt = debtHelper.findDebt(id, isAdmin);

        if (req.totalAmount() != null) {
            if (req.totalAmount() <= 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tổng số tiền nợ phải lớn hơn 0");
            }
            if (req.totalAmount() < debt.getPaidAmount()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tổng số tiền không thể nhỏ hơn số tiền đã trả");
            }
            debt.setTotalAmount(req.totalAmount());

            // Tự động đồng bộ lại trạng thái nợ theo số tiền mới
            if (debt.getPaidAmount() >= req.totalAmount()) {
                if (debt.getStatus() != DebtStatus.COMPLETED) {
                    applicationEventPublisher.publishEvent(new DebtCompletedEvent(
                            debt.getId(),
                            debt.getUserId(),
                            debt.getName(),
                            req.totalAmount(),
                            debt.getType()
                    ));
                }
                debt.setStatus(DebtStatus.COMPLETED);
            } else {
                debt.setStatus(DebtStatus.INPROCESS);
            }
        }

        if (req.name() != null)
            debt.setName(req.name());
        if (req.dueDate() != null && req.dueDate().isAfter(debt.getIssuedDate()))
            debt.setDueDate(req.dueDate());
        if (req.note() != null)
            debt.setNote(req.note());

        debtRepository.save(debt);

        return debtMapper.toRes(debt);
    }

    @Transactional
    @Override
    public void delete(UUID id, boolean isAdmin) {
        Debt debt = debtHelper.findDebt(id, isAdmin);

        // Chỉ chặn khi: nợ chưa hoàn thành VÀ đã phát sinh đợt thanh toán
        if (debt.getStatus() != DebtStatus.COMPLETED && debt.getPaidAmount() != 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "Không thể xóa khoản nợ đang trong quá trình thanh toán");
        }

        debt.setIsDeleted(true);
        debtRepository.save(debt);
    }

    @Override
    public void sendDueReminders() {
        List<Debt> debts = debtRepository.findAllOutstandingWithDueDate();
        for (Debt debt : debts) {
            try {
                if (debt == null || debt.getDueDate() == null) {
                    continue;
                }

                long daysUntilDue = ChronoUnit.DAYS.between(LocalDate.now(VIETNAM_ZONE), debt.getDueDate());
                String debtName = debt.getName() != null && !debt.getName().isBlank() ? debt.getName() : "đối tác";
                String content;

                if (daysUntilDue == 7) {
                    content = "Còn 7 ngày đến hạn khoản nợ với " + debtName + ".";
                } else if (daysUntilDue == 1) {
                    content = "Ngày mai đến hạn khoản nợ với " + debtName + ".";
                } else if (daysUntilDue <= 0 && (-daysUntilDue) % 7 == 0) {
                    content = daysUntilDue == 0
                            ? "Hôm nay đến hạn khoản nợ với " + debtName + "."
                            : "Đã quá hạn " + (-daysUntilDue) + " ngày khoản nợ với "
                            + debtName + ".";
                } else {
                    continue; // không rơi vào mốc nhắc nào trong ba mốc trên
                }

                applicationEventPublisher.publishEvent(new DebtDueReminderEvent(
                        debt.getId(),
                        debt.getUserId(),
                        debtName,
                        daysUntilDue,
                        content));
            } catch (Exception e) {
                log.error("Lỗi khi gửi nhắc nợ cho khoản {}", debt.getId(), e);
            }
        }
    }

    // -------------------------PRIVATE------------------------//

}

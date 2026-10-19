package com.datn.financeapp.module.transaction.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.category.dto.response.CateSumaryRes;
import com.datn.financeapp.module.category.service.CategoryService;
import com.datn.financeapp.module.transaction.dto.request.RecCreateReq;
import com.datn.financeapp.module.transaction.dto.request.RecUpdateReq;
import com.datn.financeapp.module.transaction.dto.response.RecDetailRes;
import com.datn.financeapp.module.transaction.entity.RecTxn;
import com.datn.financeapp.module.transaction.events.RecTxnCreateEvent;
import com.datn.financeapp.module.transaction.events.RecTxnUpdateEvent;
import com.datn.financeapp.module.transaction.mapper.RecurringMapper;
import com.datn.financeapp.module.transaction.repository.RecTxnRepository;
import com.datn.financeapp.module.transaction.repository.RecTxnScheduleRepository;
import com.datn.financeapp.module.transaction.service.RecTxnBehavierService;
import com.datn.financeapp.module.wallet.dto.response.WalletSumaryRes;
import com.datn.financeapp.module.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecTxnBehavierImpl implements RecTxnBehavierService {

    private final RecTxnRepository recurringRepository;
    private final RecTxnScheduleRepository scheduleRepository;
    private final WalletService walletService;
    private final CategoryService categoryService;
    private final RecurringMapper recurringMapper;
    private final ApplicationEventPublisher publisher;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public RecDetailRes get(UUID id, boolean isAdmin) {
        RecTxn rec = getRecTxn(id, isAdmin);

        WalletSumaryRes wallet = walletService.findSumaryByIdAndUser(rec.getWalletId(), rec.getUserId(), false)
                .orElse(null);
        CateSumaryRes cate = (rec.getCategoryCode() == null || rec.getCategoryCode().isBlank()) ? null
                : categoryService.findByCodeAndIsDeletedAndIsLeaf(rec.getCategoryCode(), false, false).orElse(null);

        return recurringMapper.toDetalRes(rec, cate, wallet);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public RecDetailRes create(RecCreateReq req, boolean isAdmin) {
        log.debug("tạo recTxn");
        UUID userId = SecurityContextUtil.currentUserId();

        WalletSumaryRes wallet = walletService.findSumaryByIdAndUser(req.walletId(), userId, false)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Ví không tồn tại hoặc đã bị xoá."));

        CateSumaryRes cate = null;
        if (req.categoryCode() != null && !req.categoryCode().isBlank()) {
            cate = categoryService.findByCodeAndIsDeletedAndIsLeaf(req.categoryCode(), false, true)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Danh mục không tồn tại hoặc không phải danh mục con."));
        }

        RecTxn rec = recurringMapper.toEntity(req, UUID.randomUUID(), userId);
        recurringRepository.save(rec);

        log.debug("recurring save {}, start create RecTxnSchedule", rec);
        publisher.publishEvent(new RecTxnCreateEvent(rec));
        log.debug("create schedule success");

        return recurringMapper.toDetalRes(rec, cate, wallet);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public RecDetailRes update(UUID id, RecUpdateReq req, boolean isAdmin) {
        RecTxn rec = getRecTxn(id, isAdmin);

        boolean scheduleChanged = isScheduleChanged(rec, req);
        boolean amountChanged = req.amount() != null && !req.amount().equals(rec.getAmount());

        if (req.walletId() != null) {
            walletService.findSumaryByIdAndUser(req.walletId(), rec.getUserId(), false)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Ví không tồn tại hoặc đã bị xoá."));
            rec.setWalletId(req.walletId());
        }

        if (req.categoryCode() != null) {
            if (req.categoryCode().isBlank()) {
                rec.setCategoryCode(null);
            } else {
                categoryService.findByCodeAndIsDeletedAndIsLeaf(req.categoryCode(), false, true)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Danh mục không tồn tại hoặc không phải danh mục con."));
                rec.setCategoryCode(req.categoryCode());
            }
        }

        if (req.frequency() != null && req.interval() != null) {
            rec.setInterval(req.interval());
            rec.setFrequency(req.frequency());
        }

        if (req.endAt() != null) rec.setEndAt(req.endAt());
        if (req.displayName() != null) rec.setName(req.displayName());
        if (req.amount() != null) rec.setAmount(req.amount());
        if (req.note() != null) rec.setNote(req.note());
        if (req.isEnabled() != null) rec.setIsEnabled(req.isEnabled());

        recurringRepository.saveAndFlush(rec);

        if (scheduleChanged || amountChanged) {
            publisher.publishEvent(new RecTxnUpdateEvent(rec, scheduleChanged));
        }

        WalletSumaryRes wallet = walletService.findSumaryByIdAndUser(rec.getWalletId(), rec.getUserId(), false)
                .orElse(null);
        CateSumaryRes cate = (rec.getCategoryCode() == null || rec.getCategoryCode().isBlank()) ? null
                : categoryService.findByCodeAndIsDeletedAndIsLeaf(rec.getCategoryCode(), false, false).orElse(null);

        return recurringMapper.toDetalRes(rec, cate, wallet);
    }

    @Override
    @Transactional
    public Void enable(UUID id, boolean isAdmin) {
        RecTxn rec = getRecTxn(id, isAdmin);
        rec.setIsEnabled(true);
        recurringRepository.save(rec);
        return null;
    }

    @Override
    @Transactional
    public Void disable(UUID id, boolean isAdmin) {
        RecTxn rec = getRecTxn(id, isAdmin);
        rec.setIsEnabled(false);
        recurringRepository.save(rec);
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public Void delete(UUID id, boolean isAdmin) {
        RecTxn rec = getRecTxn(id, isAdmin);
        rec.setIsDeleted(true);
        recurringRepository.save(rec);

        // R6: Dọn dẹp mềm các buổi chưa chạy để không bị treo
        scheduleRepository.deleteUnprocessed(rec.getId());

        return null;
    }

    /**
     * Tìm giao dịch định kỳ và kiểm tra quyền sở hữu.
     */
    private RecTxn getRecTxn(UUID id, boolean isAdmin) {
        RecTxn rec = recurringRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch định kỳ."));

        if (!isAdmin) {
            UUID currentUserId = SecurityContextUtil.currentUserId();
            if (!rec.getUserId().equals(currentUserId)) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch định kỳ.");
            }
        }
        return rec;
    }

    /**
     * Kiểm tra request có đổi tần suất, bước lặp hoặc ngày kết thúc so với cấu hình hiện tại không.
     */
    private boolean isScheduleChanged(RecTxn rec, RecUpdateReq req) {
        return (req.frequency() != null && req.frequency() != rec.getFrequency())
                || (req.interval() != null && !req.interval().equals(rec.getInterval()))
                || (req.endAt() != null && !req.endAt().equals(rec.getEndAt()));
    }
}

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
import com.datn.financeapp.module.transaction.mapper.RecurringMapper;
import com.datn.financeapp.module.transaction.repository.RecTxnRepository;
import com.datn.financeapp.module.transaction.service.RecTxnBehavierService;
import com.datn.financeapp.module.transaction.service.RecTxnScheduleService;
import com.datn.financeapp.module.wallet.dto.response.WalletSumaryRes;
import com.datn.financeapp.module.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecTxnBehavierImpl implements RecTxnBehavierService {

    private final RecTxnRepository recurringRepository;
    private final RecTxnScheduleService scheduleService;
    private final WalletService walletService;
    private final CategoryService categoryService;
    private final RecurringMapper recurringMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public RecDetailRes detail(UUID id, UUID userId) {
        var rec = recurringRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy recurring"));

        if (userId == null)
            userId = SecurityContextUtil.currentUserId();

        var wallet = walletService.findSumary(rec.getWalletId(), userId);
        var cate = categoryService.findIfIsleaf(rec.getCategoryId());

        return recurringMapper.toDetalRes(rec, cate, wallet);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public RecDetailRes create(UUID userId, RecCreateReq req) {

        RecTxn rec = recurringMapper.toEntity(req);
        rec.setId(UUID.randomUUID());

        // user tự tạo thì phải lấy id theo token gửi lên để check
        if (userId == null)
            userId = SecurityContextUtil.currentUserId();

        var cate = categoryService.findIfIsleaf(req.categoryId());
        var wallet = walletService.findSumary(req.walletId(), userId);
        rec.setUserId(userId);

        recurringRepository.save(rec);

        // tự động sinh các buổi lịch con ban đầu
        scheduleService.generateInitialSchedules(rec);

        return recurringMapper.toDetalRes(rec, cate, wallet);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public RecDetailRes update(UUID userId, UUID id, RecUpdateReq req) {

        if (userId == null)
            userId = SecurityContextUtil.currentUserId();

        WalletSumaryRes wallet = null;
        CateSumaryRes cate = null;

        var rec = recurringRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy recurring"));

        if (req.displayName() != null) {
            rec.setName(req.displayName());
        }
        if (req.amount() != null) {
            rec.setAmount(req.amount());
        }
        if (req.note() != null) {
            rec.setNote(req.note());
        }
        if (req.walletId() != null) {
            wallet = walletService.findSumary(req.walletId(), userId);
            rec.setWalletId(req.walletId());
        }
        if (req.categoryId() != null) {
            cate = categoryService.findIfIsleaf(req.categoryId());
            rec.setCategoryId(req.categoryId());
        }
        if (req.frequency() != null && req.interval() != null) {
            rec.setInterval(req.interval());
            rec.setFrequency(req.frequency());
        }

        if (req.endAt() != null) {
            rec.setEndAt(req.endAt());
        }

        recurringRepository.saveAndFlush(rec);

        return recurringMapper.toDetalRes(rec, cate, wallet);
    }

    @Override
    public Void enable(UUID userId, UUID id) {

        // user tự tạo thì phải lấy id theo token gửi lên để check
        if (userId == null)
            userId = SecurityContextUtil.currentUserId();

        var rec = recurringRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy recurring"));

        rec.setIsEnabled(true);
        recurringRepository.save(rec);

        return null;
    }

    @Override
    public Void disable(UUID userId, UUID id) {
        // user tự tạo thì phải lấy id theo token gửi lên để check
        if (userId == null)
            userId = SecurityContextUtil.currentUserId();

        var rec = recurringRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy recurring"));

        rec.setIsEnabled(false);
        recurringRepository.save(rec);

        return null;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public Void delete(UUID userId, UUID id) {

        // user tự tạo thì phải lấy id theo token gửi lên để check
        if (userId == null)
            userId = SecurityContextUtil.currentUserId();

        var rec = recurringRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy recurring"));

        rec.setIsDeleted(true);
        recurringRepository.save(rec);

        return null;
    }

}


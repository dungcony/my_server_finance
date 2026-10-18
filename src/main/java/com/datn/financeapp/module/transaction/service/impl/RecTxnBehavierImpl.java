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
import com.datn.financeapp.module.transaction.service.RecTxnBehavierService;
import com.datn.financeapp.module.transaction.service.RecTxnScheduleService;
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
    private final WalletService walletService;
    private final CategoryService categoryService;
    private final RecurringMapper recurringMapper;

    private final ApplicationEventPublisher publisher;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public RecDetailRes get(UUID id, UUID userId) {

        var rec = recurringRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy recurring"));

        if (userId == null)
            userId = SecurityContextUtil.currentUserId();

        // vì là lấy nên nếu k có thì truyền vào null
        var wallet = walletService.findSumaryByIdAndUser(rec.getWalletId(), userId);
        var cate = categoryService.findSumaryActiveById(rec.getCategoryId());

        return recurringMapper.toDetalRes(rec, cate, wallet);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public RecDetailRes create(UUID userId, RecCreateReq req) {

        log.debug("tạo recTxn");
        // user tự tạo thì phải lấy id theo token gửi lên để check
        if (userId == null)
            userId = SecurityContextUtil.currentUserId();

        var cate = categoryService.findIfIsleaf(req.categoryId()); // khi tạo thì phải là lá
        var wallet = walletService.findSumaryIfWalletBelongToUserId(req.walletId(), userId);

        RecTxn rec = recurringMapper.toEntity(req, UUID.randomUUID(), userId);

        recurringRepository.save(rec);

        log.debug("recurring save {}, start create RecTxnSchedule", rec);
        // tự động sinh các buổi lịch con ban đầu
        publisher.publishEvent(new RecTxnCreateEvent(rec));
        log.debug("create schedule success");

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

        // so với giá trị cũ trước khi ghi đè
        boolean scheduleChanged = isScheduleChanged(rec, req);
        boolean amountChanged = req.amount() != null && !req.amount().equals(rec.getAmount());

        if (req.walletId() != null) {
            wallet = walletService.findSumaryIfWalletBelongToUserId(req.walletId(), userId);
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

        if (req.endAt() != null) rec.setEndAt(req.endAt());
        if (req.displayName() != null) rec.setName(req.displayName());
        if (req.amount() != null) rec.setAmount(req.amount());
        if (req.note() != null) rec.setNote(req.note());

        recurringRepository.saveAndFlush(rec);

        // đổi số tiền hoặc lịch lặp thì đồng bộ lại các buổi chưa chạy
        if (scheduleChanged || amountChanged)
            publisher.publishEvent(new RecTxnUpdateEvent(rec, scheduleChanged));

        return recurringMapper.toDetalRes(rec, cate, wallet);
    }

    /**
     * Kiểm tra request có đổi tần suất, bước lặp hoặc ngày kết thúc so với cấu hình hiện tại không.
     */
    private boolean isScheduleChanged(RecTxn rec, RecUpdateReq req) {
        return (req.frequency() != null && req.frequency() != rec.getFrequency())
                || (req.interval() != null && !req.interval().equals(rec.getInterval()))
                || (req.endAt() != null && !req.endAt().equals(rec.getEndAt()));
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


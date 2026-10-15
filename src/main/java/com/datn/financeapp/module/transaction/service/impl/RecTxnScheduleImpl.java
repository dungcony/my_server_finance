package com.datn.financeapp.module.transaction.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.response.PageMeta;
import com.datn.financeapp.module.transaction.dto.response.RecTxnScheduleListRec;
import com.datn.financeapp.module.transaction.entity.RecTxn;
import com.datn.financeapp.module.transaction.entity.RecTxnSchedule;
import com.datn.financeapp.module.transaction.entity.Transaction;
import com.datn.financeapp.module.transaction.enums.RecTxnScheduleStatus;
import com.datn.financeapp.module.transaction.enums.TxnSource;
import com.datn.financeapp.module.transaction.helper.RecScheduleCalculator;
import com.datn.financeapp.module.transaction.mapper.RecTxnScheduleMapper;
import com.datn.financeapp.module.transaction.repository.RecTxnRepository;
import com.datn.financeapp.module.transaction.repository.RecTxnScheduleRepository;
import com.datn.financeapp.module.transaction.repository.TransactionRepository;
import com.datn.financeapp.module.transaction.service.RecTxnScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cài đặt các nghiệp vụ quản lý lịch phát sinh cho giao dịch định kỳ.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RecTxnScheduleImpl implements RecTxnScheduleService {

    private final RecTxnScheduleRepository scheduleRepository;
    private final RecTxnRepository recTxnRepository;
    private final RecTxnScheduleMapper scheduleMapper;
    private final RecScheduleCalculator scheduleCalculator;
    private final TransactionRepository transactionRepository;

    /**
     * {@inheritDoc}
     *
     * @throws BusinessException nếu không tìm thấy giao dịch định kỳ cha
     */
    @Override
    public RecTxnScheduleListRec findAll(UUID recId, Pageable pageable) {
        // kiểm tra giao dịch định kỳ cha có tồn tại
        recTxnRepository.findById(recId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch định kỳ."));

        Page<RecTxnSchedule> page = scheduleRepository.findByRecTxnIdAndIsDeletedFalse(recId, pageable);

        return new RecTxnScheduleListRec(
                scheduleMapper.toResponseList(page.getContent()),
                PageMeta.from(page)
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void generateInitialSchedules(RecTxn recTxn) {
        List<RecTxnSchedule> schedules = new ArrayList<>();
        Instant current = recTxn.getStartAt();
        int count = 0;
        // số lượng đệm tối đa nếu lặp vô hạn
        int maxBuffer = 12;

        while (true) {
            // điều kiện dừng nếu có ngày kết thúc cụ thể
            if (recTxn.getEndAt() != null && current.isAfter(recTxn.getEndAt())) {
                break;
            }
            // điều kiện dừng nếu lặp vô hạn
            if (recTxn.getEndAt() == null && count >= maxBuffer) {
                break;
            }

            RecTxnSchedule schedule = RecTxnSchedule.builder()
                    .runAt(current)
                    .amount(recTxn.getAmount())
                    .status(RecTxnScheduleStatus.PENDING)
                    .userId(recTxn.getUserId())
                    .recTxn(recTxn)
                    .build();

            schedule.setId(UUID.randomUUID());
            schedule.setIsDeleted(false);
            schedules.add(schedule);

            current = scheduleCalculator.nextOccurrence(current, recTxn.getFrequency(), recTxn.getInterval());
            count++;
        }

        scheduleRepository.saveAll(schedules);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void processDueSchedules() {
        List<RecTxnSchedule> dueList = scheduleRepository.findByStatusAndRunAtLessThanEqualAndIsDeletedFalse(
                RecTxnScheduleStatus.PENDING,
                Instant.now()
        );

        for (RecTxnSchedule schedule : dueList) {
            try {
                processSingleSchedule(schedule);
            } catch (Exception ex) {
                log.error("Lỗi khi xử lý buổi lịch định kỳ id: {}", schedule.getId(), ex);
            }
        }
    }

    private void processSingleSchedule(RecTxnSchedule schedule) {
        RecTxn rec = schedule.getRecTxn();
        if (rec == null || !Boolean.TRUE.equals(rec.getIsEnabled()) || Boolean.TRUE.equals(rec.getIsDeleted())) {
            return;
        }

        // tạo Transaction thực tế
        Transaction txn = Transaction.builder()
                .userId(schedule.getUserId())
                .walletId(rec.getWalletId())
                .categoryId(rec.getCategoryId())
                .type(rec.getType())
                .amount(schedule.getAmount())
                .name(rec.getName())
                .note(rec.getNote())
                .occurredAt(schedule.getRunAt())
                .isDeleted(false)
                .build();
        txn.setId(UUID.randomUUID());
        transactionRepository.save(txn);

        // cập nhật trạng thái buổi lịch thành SUCCESS
        schedule.setStatus(RecTxnScheduleStatus.SUCCESS);
        scheduleRepository.save(schedule);

        // nếu lặp vô hạn thì bù thêm buổi mới gối đầu
        if (rec.getEndAt() == null) {
            scheduleRepository.findTopByRecTxnIdAndIsDeletedFalseOrderByRunAtDesc(rec.getId())
                    .ifPresent(latest -> {
                        Instant nextRun = scheduleCalculator.nextOccurrence(latest.getRunAt(), rec.getFrequency(), rec.getInterval());
                        RecTxnSchedule newSchedule = RecTxnSchedule.builder()
                                .runAt(nextRun)
                                .amount(rec.getAmount())
                                .status(RecTxnScheduleStatus.PENDING)
                                .userId(rec.getUserId())
                                .recTxn(rec)
                                .build();
                        newSchedule.setId(UUID.randomUUID());
                        newSchedule.setIsDeleted(false);
                        scheduleRepository.save(newSchedule);
                    });
        } else {
            // nếu có ngày kết thúc và không còn buổi PENDING nào thì hoàn thành chu kỳ
            long remaining = scheduleRepository.countByRecTxnIdAndStatusAndIsDeletedFalse(rec.getId(), RecTxnScheduleStatus.PENDING);
            if (remaining == 0) {
                rec.setIsEnabled(false);
                recTxnRepository.save(rec);
            }
        }
    }
}

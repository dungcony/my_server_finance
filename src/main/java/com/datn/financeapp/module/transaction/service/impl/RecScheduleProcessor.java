package com.datn.financeapp.module.transaction.service.impl;

import com.datn.financeapp.module.transaction.entity.RecTxn;
import com.datn.financeapp.module.transaction.entity.RecTxnSchedule;
import com.datn.financeapp.module.transaction.entity.Transaction;
import com.datn.financeapp.module.transaction.enums.RecTxnScheduleStatus;
import com.datn.financeapp.module.transaction.helper.RecScheduleCalculator;
import com.datn.financeapp.module.transaction.repository.RecTxnRepository;
import com.datn.financeapp.module.transaction.repository.RecTxnScheduleRepository;
import com.datn.financeapp.module.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Xử lý từng buổi lịch định kỳ trong một Transaction riêng biệt (REQUIRES_NEW).
 * Đảm bảo lỗi của một buổi không làm hỏng cả lô (R2).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecScheduleProcessor {

    private final RecTxnScheduleRepository scheduleRepository;
    private final RecTxnRepository recTxnRepository;
    private final TransactionRepository transactionRepository;
    private final RecScheduleCalculator scheduleCalculator;

    private static final int MAX_BUFFER = 12;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processSingleSchedule(UUID scheduleId) {
        RecTxnSchedule schedule = scheduleRepository.findByIdAndIsDeletedFalse(scheduleId)
                .orElse(null);

        if (schedule == null || schedule.getStatus() != RecTxnScheduleStatus.PENDING) {
            return;
        }

        RecTxn rec = schedule.getRecTxn();

        // R6: Nếu định kỳ cha bị xoá mềm -> chuyển buổi này thành SKIPPED
        if (rec == null || Boolean.TRUE.equals(rec.getIsDeleted())) {
            schedule.setStatus(RecTxnScheduleStatus.SKIPPED);
            scheduleRepository.save(schedule);
            return;
        }

        // R6: Nếu định kỳ cha bị tắt (disable) -> buổi đến hạn chuyển sang SKIPPED để tránh quét lại mỗi giờ
        if (!Boolean.TRUE.equals(rec.getIsEnabled())) {
            schedule.setStatus(RecTxnScheduleStatus.SKIPPED);
            scheduleRepository.save(schedule);
            return;
        }

        // Tạo giao dịch Transaction thực tế
        Transaction txn = Transaction.builder()
                .userId(schedule.getUserId())
                .walletId(rec.getWalletId())
                .categoryCode(rec.getCategoryCode())
                .type(rec.getType())
                .amount(schedule.getAmount())
                .name(rec.getName())
                .note(rec.getNote())
                .occurredAt(schedule.getRunAt())
                .build();
        txn.setId(UUID.randomUUID());
        transactionRepository.save(txn);

        // Cập nhật trạng thái buổi lịch thành SUCCESS
        schedule.setStatus(RecTxnScheduleStatus.SUCCESS);
        scheduleRepository.save(schedule);

        // R4: Duy trì buffer gối đầu (tối đa MAX_BUFFER buổi PENDING)
        long pendingCount = scheduleRepository.countByRecTxnIdAndStatusAndIsDeletedFalse(rec.getId(), RecTxnScheduleStatus.PENDING);
        if (pendingCount < MAX_BUFFER) {
            scheduleRepository.findTopByRecTxnIdAndIsDeletedFalseOrderByRunAtDesc(rec.getId())
                    .ifPresent(latest -> {
                        Instant nextRun = scheduleCalculator.nextOccurrence(latest.getRunAt(), rec.getFrequency(), rec.getInterval());

                        // Nếu lặp vô hạn HOẶC chưa vượt quá endAt thì sinh tiếp 1 buổi gối đầu
                        if (rec.getEndAt() == null || !nextRun.isAfter(rec.getEndAt())) {
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
                        } else if (pendingCount == 0) {
                            // Nếu có endAt và không còn buổi nào nữa thì hoàn tất cấu hình định kỳ
                            rec.setIsEnabled(false);
                            recTxnRepository.save(rec);
                        }
                    });
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID scheduleId) {
        scheduleRepository.findByIdAndIsDeletedFalse(scheduleId).ifPresent(schedule -> {
            schedule.setStatus(RecTxnScheduleStatus.FAILED);
            scheduleRepository.save(schedule);
        });
    }
}

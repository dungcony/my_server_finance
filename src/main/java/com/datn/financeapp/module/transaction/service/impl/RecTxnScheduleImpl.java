package com.datn.financeapp.module.transaction.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.response.PageMeta;
import com.datn.financeapp.module.transaction.dto.response.RecTxnScheduleListRec;
import com.datn.financeapp.module.transaction.entity.RecTxn;
import com.datn.financeapp.module.transaction.entity.RecTxnSchedule;
import com.datn.financeapp.module.transaction.enums.RecTxnScheduleStatus;
import com.datn.financeapp.module.transaction.events.RecTxnCreateEvent;
import com.datn.financeapp.module.transaction.events.RecTxnUpdateEvent;
import com.datn.financeapp.module.transaction.helper.RecScheduleCalculator;
import com.datn.financeapp.module.transaction.mapper.RecTxnScheduleMapper;
import com.datn.financeapp.module.transaction.repository.RecTxnRepository;
import com.datn.financeapp.module.transaction.repository.RecTxnScheduleRepository;
import com.datn.financeapp.module.transaction.service.RecTxnScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
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
    private final RecScheduleProcessor scheduleProcessor;

    private static final int MAX_BUFFER = 12;

    /**
     * {@inheritDoc}
     *
     * @throws BusinessException nếu không tìm thấy giao dịch định kỳ cha
     */
    @Override
    public RecTxnScheduleListRec findAll(UUID recId, Pageable pageable) {
        recTxnRepository.findById(recId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch định kỳ."));

        Page<RecTxnSchedule> page = scheduleRepository.findByRecTxnIdAndIsDeletedFalse(recId, pageable);

        return new RecTxnScheduleListRec(
                scheduleMapper.toResponseList(page.getContent()),
                PageMeta.from(page)
        );
    }

    @Transactional
    @EventListener
    public void generateInitialSchedules(RecTxnCreateEvent event) {
        RecTxn recTxn = event.recTxn();
        scheduleRepository.saveAll(buildSchedules(recTxn, recTxn.getStartAt()));
    }

    /**
     * Đồng bộ các buổi chưa chạy sau khi sửa giao dịch định kỳ.
     */
    @Transactional
    @EventListener
    public void syncSchedules(RecTxnUpdateEvent event) {
        RecTxn recTxn = event.recTxn();

        // chỉ đổi số tiền thì cập nhật thẳng, giữ nguyên ngày chạy
        if (!event.scheduleChanged()) {
            scheduleRepository.updateUnprocessedAmount(recTxn.getId(), recTxn.getAmount());
            return;
        }

        scheduleRepository.deleteUnprocessed(recTxn.getId());

        // mốc là buổi đã chạy gần nhất, chưa chạy buổi nào thì bắt đầu lại từ startAt
        Instant from = scheduleRepository.findTopByRecTxnIdAndIsDeletedFalseOrderByRunAtDesc(recTxn.getId())
                .map(latest -> scheduleCalculator.nextOccurrence(latest.getRunAt(), recTxn.getFrequency(), recTxn.getInterval()))
                .orElse(recTxn.getStartAt());

        scheduleRepository.saveAll(buildSchedules(recTxn, from));
    }

    /**
     * R2: Scheduler chạy từng buổi trong transaction độc lập.
     * Không đặt @Transactional ở mức method này để một buổi lỗi không làm rollback cả lô.
     */
    @Override
    public void processDueSchedules() {
        List<RecTxnSchedule> dueList = scheduleRepository.findByStatusAndRunAtLessThanEqualAndIsDeletedFalse(
                RecTxnScheduleStatus.PENDING,
                Instant.now()
        );

        for (RecTxnSchedule schedule : dueList) {
            try {
                scheduleProcessor.processSingleSchedule(schedule.getId());
            } catch (Exception ex) {
                log.error("Lỗi khi xử lý buổi lịch định kỳ id: {}", schedule.getId(), ex);
                try {
                    scheduleProcessor.markFailed(schedule.getId());
                } catch (Exception markEx) {
                    log.error("Không thể cập nhật trạng thái FAILED cho buổi lịch id: {}", schedule.getId(), markEx);
                }
            }
        }
    }

    /**
     * R4: Dựng danh sách buổi PENDING tối đa MAX_BUFFER (12) buổi đệm.
     */
    private List<RecTxnSchedule> buildSchedules(RecTxn recTxn, Instant from) {
        List<RecTxnSchedule> schedules = new ArrayList<>();
        Instant current = from;
        int count = 0;

        while (count < MAX_BUFFER) {
            // điều kiện dừng nếu có ngày kết thúc cụ thể
            if (recTxn.getEndAt() != null && current.isAfter(recTxn.getEndAt())) {
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

        return schedules;
    }
}

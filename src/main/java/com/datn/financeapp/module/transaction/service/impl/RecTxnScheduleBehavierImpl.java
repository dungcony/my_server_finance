package com.datn.financeapp.module.transaction.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.response.PageMeta;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.transaction.dto.request.RecTxnScheduleCreateReq;
import com.datn.financeapp.module.transaction.dto.request.RecTxnScheduleUpdateReq;
import com.datn.financeapp.module.transaction.dto.response.RecTxnScheduleListRec;
import com.datn.financeapp.module.transaction.dto.response.RecTxnScheduleRes;
import com.datn.financeapp.module.transaction.entity.RecTxn;
import com.datn.financeapp.module.transaction.entity.RecTxnSchedule;
import com.datn.financeapp.module.transaction.enums.RecTxnScheduleStatus;
import com.datn.financeapp.module.transaction.mapper.RecTxnScheduleMapper;
import com.datn.financeapp.module.transaction.repository.RecTxnRepository;
import com.datn.financeapp.module.transaction.repository.RecTxnScheduleRepository;
import com.datn.financeapp.module.transaction.service.RecTxnScheduleBehavierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Cài đặt các hành vi nghiệp vụ quản lý buổi lịch định kỳ (tạo, sửa, xóa, skip, unskip) có phân quyền người dùng và admin.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RecTxnScheduleBehavierImpl implements RecTxnScheduleBehavierService {

    private final RecTxnScheduleRepository scheduleRepository;
    private final RecTxnRepository recTxnRepository;
    private final RecTxnScheduleMapper scheduleMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public RecTxnScheduleRes create(UUID recId, RecTxnScheduleCreateReq req, boolean isAdmin) {
        RecTxn recTxn = recTxnRepository.findByIdAndIsDeletedFalse(recId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch định kỳ."));

        if (!isAdmin) {
            UUID currentUserId = SecurityContextUtil.currentUserId();
            if (!recTxn.getUserId().equals(currentUserId)) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch định kỳ.");
            }
        }

        RecTxnSchedule schedule = scheduleMapper.toEntity(req);
        schedule.setId(UUID.randomUUID());
        schedule.setUserId(recTxn.getUserId());
        schedule.setRecTxn(recTxn);
        schedule.setStatus(RecTxnScheduleStatus.PENDING);
        schedule.setIsDeleted(false);

        RecTxnSchedule saved = scheduleRepository.save(schedule);
        return scheduleMapper.toResponse(saved);
    }

    @Override
    public RecTxnScheduleListRec gets(UUID recId, Pageable pageable, boolean isAdmin) {
        Page<RecTxnSchedule> recTxnSchedules;

        if (isAdmin) {
            recTxnSchedules = scheduleRepository.findByRecTxnIdAndIsDeletedFalse(recId, pageable);
        } else {
            UUID currentUserId = SecurityContextUtil.currentUserId();
            recTxnSchedules = scheduleRepository.findByUserIdAndRecTxnIdAndIsDeletedFalse(currentUserId, recId, pageable);
        }

        return new RecTxnScheduleListRec(
                recTxnSchedules.getContent().stream()
                        .map(scheduleMapper::toResponse)
                        .toList(),
                PageMeta.from(recTxnSchedules)
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public RecTxnScheduleRes update(UUID scheduleId, RecTxnScheduleUpdateReq req, boolean isAdmin) {
        RecTxnSchedule schedule = getSchedule(scheduleId, isAdmin);

        // R8: Chỉ cho phép sửa nếu buổi lịch đang PENDING
        if (schedule.getStatus() != RecTxnScheduleStatus.PENDING) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Chỉ có thể chỉnh sửa buổi lịch đang chờ xử lý (PENDING).");
        }

        if (req.runAt() != null) {
            schedule.setRunAt(req.runAt());
        }
        if (req.amount() != null) {
            schedule.setAmount(req.amount());
        }

        RecTxnSchedule saved = scheduleRepository.save(schedule);
        return scheduleMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public Void skip(UUID scheduleId, boolean isAdmin) {
        RecTxnSchedule schedule = getSchedule(scheduleId, isAdmin);

        if (schedule.getStatus() != RecTxnScheduleStatus.PENDING) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Chỉ có thể bỏ qua buổi lịch đang ở trạng thái PENDING.");
        }

        schedule.setStatus(RecTxnScheduleStatus.SKIPPED);
        scheduleRepository.save(schedule);
        return null;
    }

    @Override
    @Transactional
    public Void unskip(UUID scheduleId, boolean isAdmin) {
        RecTxnSchedule schedule = getSchedule(scheduleId, isAdmin);

        if (schedule.getStatus() != RecTxnScheduleStatus.SKIPPED) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Chỉ có thể khôi phục buổi lịch đang ở trạng thái SKIPPED.");
        }

        schedule.setStatus(RecTxnScheduleStatus.PENDING);
        scheduleRepository.save(schedule);
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public Void delete(UUID scheduleId, boolean isAdmin) {
        RecTxnSchedule schedule = getSchedule(scheduleId, isAdmin);
        schedule.setIsDeleted(true);
        scheduleRepository.save(schedule);
        return null;
    }

    /**
     * Tìm buổi lịch và kiểm tra quyền sở hữu.
     */
    private RecTxnSchedule getSchedule(UUID scheduleId, boolean isAdmin) {
        RecTxnSchedule schedule = scheduleRepository.findByIdAndIsDeletedFalse(scheduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy buổi lịch cần xử lý."));

        if (!isAdmin) {
            UUID currentUserId = SecurityContextUtil.currentUserId();
            if (!schedule.getUserId().equals(currentUserId)) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy buổi lịch cần xử lý.");
            }
        }
        return schedule;
    }
}

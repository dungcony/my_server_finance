package com.datn.financeapp.module.transaction.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.transaction.dto.request.RecTxnScheduleCreateReq;
import com.datn.financeapp.module.transaction.dto.request.RecTxnScheduleUpdateReq;
import com.datn.financeapp.module.transaction.dto.response.RecTxnScheduleRes;
import com.datn.financeapp.module.transaction.entity.RecTxnSchedule;
import com.datn.financeapp.module.transaction.enums.RecTxnScheduleStatus;
import com.datn.financeapp.module.transaction.mapper.RecTxnScheduleMapper;
import com.datn.financeapp.module.transaction.repository.RecTxnRepository;
import com.datn.financeapp.module.transaction.repository.RecTxnScheduleRepository;
import com.datn.financeapp.module.transaction.service.RecTxnScheduleBehavierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Cài đặt các hành vi nghiệp vụ quản lý buổi lịch định kỳ (tạo, sửa, xóa) có phân quyền người dùng và admin.
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
     *
     * @throws BusinessException nếu không tìm thấy giao dịch định kỳ cha hoặc không thuộc quyền sở hữu
     */
    @Override
    @Transactional
    public RecTxnScheduleRes create(UUID userId, UUID recId, RecTxnScheduleCreateReq req) {
        // người dùng tự gọi thì lấy id theo token trong SecurityContext
        if (userId == null) {
            userId = SecurityContextUtil.currentUserId();
        }

        // kiểm tra giao dịch định kỳ cha có tồn tại và thuộc quyền sở hữu
        var recTxn = recTxnRepository.findByIdAndUserId(recId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch định kỳ."));

        RecTxnSchedule schedule = scheduleMapper.toEntity(req);
        schedule.setId(UUID.randomUUID());
        schedule.setUserId(recTxn.getUserId());
        schedule.setRecTxn(recTxn);
        schedule.setIsDeleted(false);

        // thiết lập trạng thái mặc định nếu client không truyền
        if (schedule.getStatus() == null) {
            schedule.setStatus(RecTxnScheduleStatus.PENDING);
        }

        RecTxnSchedule saved = scheduleRepository.save(schedule);
        return scheduleMapper.toResponse(saved);
    }

    /**
     * {@inheritDoc}
     *
     * @throws BusinessException nếu không tìm thấy buổi lịch hoặc không thuộc quyền sở hữu
     */
    @Override
    @Transactional
    public RecTxnScheduleRes update(UUID userId, UUID scheduleId, RecTxnScheduleUpdateReq req) {
        // người dùng tự gọi thì lấy id theo token trong SecurityContext
        if (userId == null) {
            userId = SecurityContextUtil.currentUserId();
        }

        // tìm buổi lịch theo ID và quyền sở hữu người dùng
        RecTxnSchedule schedule = scheduleRepository.findByIdAndUserIdAndIsDeletedFalse(scheduleId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy buổi lịch cần cập nhật."));

        // cập nhật các trường được phép thay đổi
        if (req.runAt() != null) {
            schedule.setRunAt(req.runAt());
        }
        if (req.amount() != null) {
            schedule.setAmount(req.amount());
        }
        if (req.status() != null) {
            schedule.setStatus(req.status());
        }

        RecTxnSchedule saved = scheduleRepository.save(schedule);
        return scheduleMapper.toResponse(saved);
    }

    /**
     * {@inheritDoc}
     *
     * @throws BusinessException nếu không tìm thấy buổi lịch hoặc không thuộc quyền sở hữu
     */
    @Override
    @Transactional
    public Void delete(UUID userId, UUID scheduleId) {
        // người dùng tự gọi thì lấy id theo token trong SecurityContext
        if (userId == null) {
            userId = SecurityContextUtil.currentUserId();
        }

        // tìm buổi lịch theo ID và quyền sở hữu người dùng
        RecTxnSchedule schedule = scheduleRepository.findByIdAndUserIdAndIsDeletedFalse(scheduleId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy buổi lịch cần xóa."));

        // thực hiện xóa mềm
        schedule.setIsDeleted(true);
        scheduleRepository.save(schedule);

        return null;
    }
}

package com.datn.financeapp.module.transaction.service;

import com.datn.financeapp.module.transaction.dto.request.RecTxnScheduleCreateReq;
import com.datn.financeapp.module.transaction.dto.request.RecTxnScheduleUpdateReq;
import com.datn.financeapp.module.transaction.dto.response.RecTxnScheduleRes;

import java.util.UUID;

public interface RecTxnScheduleBehavierService {
    /**
     * Tạo mới thủ công một buổi lịch cho giao dịch định kỳ.
     *
     * @param userId id của người dùng nếu người gửi là admin thì là null
     * @param recId  ID giao dịch định kỳ cha
     * @param req    thông tin yêu cầu tạo buổi lịch
     * @return thông tin chi tiết buổi lịch vừa tạo
     */
    RecTxnScheduleRes create(UUID userId, UUID recId, RecTxnScheduleCreateReq req);

    /**
     * Cập nhật thông tin (ngày chạy, số tiền, trạng thái) của một buổi lịch cụ thể.
     *
     * @param userId     id của người dùng nếu người gửi là admin thì là null
     * @param scheduleId ID buổi lịch cần cập nhật
     * @param req        thông tin cập nhật
     * @return thông tin buổi lịch sau khi cập nhật
     */
    RecTxnScheduleRes update(UUID userId, UUID scheduleId, RecTxnScheduleUpdateReq req);

    /**
     * Xóa mềm một buổi lịch cụ thể.
     *
     * @param userId     id của người dùng nếu người gửi là admin thì là null
     * @param scheduleId ID buổi lịch cần xóa
     * @return null
     */
    Void delete(UUID userId, UUID scheduleId);

}

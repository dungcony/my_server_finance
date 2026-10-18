package com.datn.financeapp.module.transaction.service;

import com.datn.financeapp.module.transaction.dto.response.RecTxnScheduleListRec;
import com.datn.financeapp.module.transaction.events.RecTxnCreateEvent;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Định nghĩa dịch vụ quản lý các buổi lịch phát sinh của giao dịch định kỳ.
 */
public interface RecTxnScheduleService {

    /**
     * Lấy danh sách phân trang các buổi lịch thuộc một giao dịch định kỳ.
     *
     * @param recId    ID giao dịch định kỳ cha
     * @param pageable thông tin phân trang
     * @return danh sách buổi lịch kèm metadata phân trang
     */
    RecTxnScheduleListRec findAll(UUID recId, Pageable pageable);

    /**
     * Quét và xử lý các buổi lịch đến hạn chạy tự động để sinh giao dịch thực tế.
     */
    void processDueSchedules();

    /**
     * Tự động sinh danh sách các buổi lịch ban đầu khi vừa tạo mới giao dịch định kỳ.
     *
     */
    void generateInitialSchedules(RecTxnCreateEvent event);
}

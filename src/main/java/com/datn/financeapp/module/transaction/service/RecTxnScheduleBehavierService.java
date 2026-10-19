package com.datn.financeapp.module.transaction.service;

import com.datn.financeapp.module.transaction.dto.request.RecTxnScheduleCreateReq;
import com.datn.financeapp.module.transaction.dto.request.RecTxnScheduleUpdateReq;
import com.datn.financeapp.module.transaction.dto.response.RecTxnScheduleListRec;
import com.datn.financeapp.module.transaction.dto.response.RecTxnScheduleRes;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface RecTxnScheduleBehavierService {
    /**
     * Tạo mới thủ công một buổi lịch cho giao dịch định kỳ.
     *
     * @param recId   ID giao dịch định kỳ cha
     * @param req     thông tin yêu cầu tạo buổi lịch
     * @param isAdmin true nếu admin gọi, false nếu user gọi
     * @return thông tin chi tiết buổi lịch vừa tạo
     */
    RecTxnScheduleRes create(UUID recId, RecTxnScheduleCreateReq req, boolean isAdmin);

    /**
     * Lấy danh sách các buổi lịch của một giao dịch định kỳ.
     *
     * @param recId    ID giao dịch định kỳ cha
     * @param pageable thông tin phân trang
     * @param isAdmin  true nếu admin gọi, false nếu user gọi
     * @return danh sách các buổi lịch
     */
    RecTxnScheduleListRec gets(UUID recId, Pageable pageable, boolean isAdmin);

    /**
     * Cập nhật thông tin (ngày chạy, số tiền) của một buổi lịch cụ thể (chỉ khi PENDING).
     *
     * @param scheduleId ID buổi lịch cần cập nhật
     * @param req        thông tin cập nhật
     * @param isAdmin    true nếu admin gọi, false nếu user gọi
     * @return thông tin buổi lịch sau khi cập nhật
     */
    RecTxnScheduleRes update(UUID scheduleId, RecTxnScheduleUpdateReq req, boolean isAdmin);

    /**
     * Bỏ qua buổi lịch (chuyển trạng thái từ PENDING sang SKIPPED).
     *
     * @param scheduleId ID buổi lịch cần bỏ qua
     * @param isAdmin    true nếu admin gọi, false nếu user gọi
     * @return Void
     */
    Void skip(UUID scheduleId, boolean isAdmin);

    /**
     * Khôi phục buổi lịch đã bỏ qua (chuyển trạng thái từ SKIPPED về PENDING).
     *
     * @param scheduleId ID buổi lịch cần khôi phục
     * @param isAdmin    true nếu admin gọi, false nếu user gọi
     * @return Void
     */
    Void unskip(UUID scheduleId, boolean isAdmin);

    /**
     * Xóa mềm một buổi lịch cụ thể.
     *
     * @param scheduleId ID buổi lịch cần xóa
     * @param isAdmin    true nếu admin gọi, false nếu user gọi
     * @return null
     */
    Void delete(UUID scheduleId, boolean isAdmin);

}

package com.datn.financeapp.module.transaction.dto.response;

import com.datn.financeapp.module.transaction.enums.RecTxnScheduleStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Phản hồi thông tin chi tiết của một buổi lịch phát sinh giao dịch định kỳ.
 *
 * @param id          ID định danh duy nhất của buổi lịch
 * @param recurringId ID của cấu hình giao dịch định kỳ gốc
 * @param userId      ID người dùng sở hữu buổi lịch
 * @param runDate     Ngày dự kiến chạy của buổi lịch (LocalDate)
 * @param amount      Số tiền áp dụng cho buổi lịch này (Đơn vị: VND)
 * @param status      Trạng thái hiện tại của buổi lịch (PENDING, SUCCESS, FAILED, SKIPPED)
 * @param createdAt   Thời điểm tạo bản ghi
 * @param updatedAt   Thời điểm cập nhật bản ghi gần nhất
 */
public record RecTxnScheduleRes(
        UUID id,
        UUID recurringId,
        UUID userId,
        LocalDate runDate,
        Long amount,
        RecTxnScheduleStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}

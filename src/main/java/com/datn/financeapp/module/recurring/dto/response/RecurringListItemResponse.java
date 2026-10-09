package com.datn.financeapp.module.recurring.dto.response;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Phản hồi thông tin item giao dịch định kỳ trong danh sách hoặc kết quả tạo/sửa.
 *
 * @param id            ID giao dịch định kỳ
 * @param displayName   tên hiển thị
 * @param type          loại giao dịch (INCOME/EXPENSE)
 * @param amount        số tiền
 * @param wallet        thông tin ví
 * @param category      thông tin danh mục
 * @param frequency     tần suất lặp
 * @param interval      khoảng thời gian lặp
 * @param scheduleLabel nhãn mô tả lịch lặp dạng chuỗi đọc được
 * @param startDate     ngày bắt đầu
 * @param endDate       ngày kết thúc
 * @param nextRunDate   ngày chạy tiếp theo
 * @param lastRunDate   ngày chạy gần nhất
 * @param daysUntilNext số ngày còn lại đến kỳ tiếp theo
 * @param isEnabled     trạng thái đang hoạt động hay tạm dừng
 * @param runCount      số lần đã phát sinh giao dịch
 * @param note          ghi chú
 */
public record RecurringListItemResponse(
        UUID id,
        String displayName,
        String type,
        Long amount,
        WalletRef wallet,
        CategoryRef category,
        String frequency,
        Integer interval,
        String scheduleLabel,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate nextRunDate,
        LocalDate lastRunDate,
        Long daysUntilNext,
        Boolean isEnabled,
        Long runCount,
        String note) {

    /**
     * Thông tin tham chiếu ví giao dịch.
     *
     * @param id   ID ví
     * @param name tên ví
     */
    public record WalletRef(UUID id, String name) {
    }

    /**
     * Thông tin tham chiếu danh mục.
     *
     * @param id    ID danh mục
     * @param name  tên danh mục
     * @param icon  biểu tượng danh mục
     * @param color màu sắc danh mục
     */
    public record CategoryRef(UUID id, String name, IconRef icon, String color) {
    }

    /**
     * Thông tin biểu tượng danh mục.
     *
     * @param code     mã biểu tượng
     * @param pathData dữ liệu SVG path của biểu tượng
     */
    public record IconRef(String code, String pathData) {
    }
}

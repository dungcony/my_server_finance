package com.datn.financeapp.module.transaction.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Yêu cầu cập nhật giao dịch định kỳ.
 * Tất cả các trường đều là tùy chọn; truyền null nếu giữ nguyên giá trị hiện tại.
 *
 * @param displayName tên hiển thị mới của giao dịch định kỳ
 * @param amount      số tiền mới của giao dịch
 * @param walletId    ID ví giao dịch mới
 * @param categoryId  ID danh mục chi tiêu/thu nhập mới
 * @param frequency   tần suất lặp mới (DAILY, WEEKLY, MONTHLY, YEARLY)
 * @param interval    khoảng thời gian giữa các lần lặp
 * @param endDate     ngày kết thúc lặp mới
 * @param note        ghi chú cập nhật
 */
public record UpdateRecurringRequest(
        @Size(min = 1, max = 100) String displayName,
        @Positive Long amount,
        UUID walletId,
        UUID categoryId,
        String frequency,
        @Positive Integer interval,
        LocalDate endDate,
        String note) {
}

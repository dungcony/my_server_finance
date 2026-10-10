package com.datn.financeapp.module.transaction.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Yêu cầu thiết lập giao dịch định kỳ mới.
 *
 * @param displayName Tên hiển thị của lịch giao dịch (tối đa 100 ký tự)
 * @param type        Loại giao dịch ({@code expense}, {@code income}, {@code transfer})
 * @param amount      Số tiền phát sinh mỗi kỳ (Đơn vị: VND)
 * @param walletId    ID ví trích/nhận tiền
 * @param categoryId  ID danh mục giao dịch
 * @param frequency   Tần suất lặp ({@code daily}, {@code weekly}, {@code monthly}, {@code yearly})
 * @param interval    Khoảng cách lặp (mặc định: 1)
 * @param startDate   Ngày bắt đầu áp dụng lịch định kỳ
 * @param endDate     Ngày kết thúc lịch định kỳ (tùy chọn)
 * @param note        Ghi chú giao dịch
 */
public record CreateRecurringRequest(
        @NotBlank @Size(min = 1, max = 100) String displayName,
        @NotBlank String type,
        @NotNull @Positive Long amount,
        @NotNull UUID walletId,
        @NotNull UUID categoryId,
        @NotBlank String frequency,
        Integer interval,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        String note) {
}

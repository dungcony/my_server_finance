package com.datn.financeapp.module.transaction.dto.request;

import com.datn.financeapp.module.transaction.enums.FrequencyType;
import com.datn.financeapp.module.transaction.enums.TxnType;
import jakarta.validation.constraints.*;

import java.time.Instant;
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
 * @param startAt     Thời gian bắt đầu áp dụng lịch định kỳ
 * @param endAt       Thời gian kết thúc lịch định kỳ (tùy chọn)
 * @param note        Ghi chú giao dịch
 */
public record RecCreateReq(
        @NotBlank(message = "Tên hiển thị không được để trống.")
        @Size(min = 1, max = 100, message = "Tên hiển thị phải từ 1 đến 100 ký tự.")
        String displayName,

        @NotNull(message = "Loại giao dịch không được để trống.")
        TxnType type,

        @NotNull(message = "Số tiền không được để trống.")
        @Positive(message = "Số tiền phải lớn hơn 0.")
        Long amount,

        @NotNull(message = "Ví không được để trống.")
        UUID walletId,

        UUID categoryId,

        @NotNull(message = "Thời gian bắt đầu không được để trống.")
        @PastOrPresent(message = "Thời gian giao dịch không được ở tương lai.")
        Instant startAt,

        @Future(message = "Thời gian kết thúc phải ở tương lai.")
        Instant endAt,

        @NotNull(message = "Đơn vị lặp không được để trống.")
        FrequencyType frequency,

        @NotNull(message = "Bước lặp không được để trống")
        @Min(value = 1, message = "Bước lặp không được < 1")
        @Max(value = 100, message = "Bước lặp k được quá 100")
        Integer interval,

        String note) {

}


package com.datn.financeapp.module.transaction.dto.request;

import com.datn.financeapp.module.transaction.enums.FrequencyType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
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
 * @param endAt       Thời gian kết thúc lặp mới
 * @param isEnable    Tắt hoặc bật lại
 * @param note        ghi chú cập nhật
 */
public record RecUpdateReq(
        @Size(min = 1, max = 100, message = "Tên hiển thị phải từ 1 đến 100 ký tự.")
        String displayName,

        @Positive(message = "Số tiền phải lớn hơn 0.")
        Long amount,

        UUID walletId,
        UUID categoryId,
        FrequencyType frequency,

        @Positive(message = "Khoảng cách lặp phải lớn hơn 0.")
        Integer interval,

        @Future(message = "Thời gian kết thúc phải ở tương lai.")
        Instant endAt,

        Boolean isEnable,
        String note) {
    // Compact Constructor: Chạy ngay lập tức khi record được khởi tạo
    public RecUpdateReq {
        if ((frequency == null) != (interval == null)) {
            throw new IllegalArgumentException("Đơn vị lặp và bước lặp phải cùng được nhập hoặc cùng để trống.");
        }
    }
}

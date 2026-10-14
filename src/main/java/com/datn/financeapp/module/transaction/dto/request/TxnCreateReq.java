package com.datn.financeapp.module.transaction.dto.request;

import com.datn.financeapp.module.transaction.enums.TransactionSource;
import com.datn.financeapp.module.transaction.enums.TransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO yêu cầu tạo mới một giao dịch tài chính cá nhân.
 *
 * @param type        Loại giao dịch (EXPENSE, INCOME, LOAN)
 * @param amount      Số tiền giao dịch; phải là số dương lớn hơn 0 (Đơn vị: VND)
 * @param walletId    ID định danh của ví phát sinh giao dịch
 * @param categoryId  ID định danh của danh mục tài chính (bắt buộc danh mục lá đối với thu/chi)
 * @param occurredAt  Thời điểm phát sinh giao dịch thực tế theo chuẩn ISO-8601
 * @param displayName Tên hoặc tiêu đề hiển thị của giao dịch (tối đa 150 ký tự)
 * @param note        Ghi chú chi tiết cho giao dịch (tối đa 500 ký tự)
 * @param source      Nguồn tạo giao dịch (MANUAL, AI, RECURRING); mặc định là MANUAL nếu không truyền
 * @param receiptUrl  Đường dẫn URL ảnh chụp biên lai / hóa đơn đính kèm (tối đa 500 ký tự)
 */
public record TxnCreateReq(
        @NotNull(message = "Loại giao dịch không được để trống.")
        TransactionType type,

        @NotNull(message = "Số tiền không được để trống.")
        @Positive(message = "Số tiền phải lớn hơn 0.")
        Long amount,

        @NotNull(message = "Ví không được để trống.")
        UUID walletId,

        @NotNull(message = "Danh mục không được để trống.")
        UUID categoryId,
        
        @PastOrPresent(message = "Thời gian giao dịch không được ở tương lai.")
        @NotNull(message = "Thời gian giao dịch không được để trống.")
        Instant occurredAt,

        @Size(max = 150, message = "Tên hiển thị tối đa 150 ký tự.")
        String displayName,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự.")
        String note,

        TransactionSource source,

        @Size(max = 500, message = "Đường dẫn biên lai tối đa 500 ký tự.")
        String receiptUrl
) {
}
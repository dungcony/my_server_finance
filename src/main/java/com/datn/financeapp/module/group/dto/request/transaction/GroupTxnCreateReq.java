package com.datn.financeapp.module.group.dto.request.transaction;

import com.datn.financeapp.module.group.enums.GroupTxnType;
import com.datn.financeapp.module.group.enums.MoneySource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO yêu cầu tạo mới giao dịch tài chính nhóm (Group Transaction).
 * <p>
 * Hỗ trợ tạo giao dịch chi tiêu chung ({@code EXPENSE}) hoặc nộp quỹ ({@code CONTRIBUTION}).
 * </p>
 *
 * @param type         Loại giao dịch (bắt buộc: EXPENSE hoặc CONTRIBUTION).
 * @param moneySource  Nguồn tiền thực hiện (FUND: trích từ quỹ nhóm, PERSONAL: thành viên tự chi trả).
 * @param amount       Số tiền giao dịch (bắt buộc, > 0, tối đa 999.999.999.999 VNĐ).
 * @param occurredAt   Thời điểm xảy ra giao dịch (UTC Instant / ISO-8601). Ưu tiên cao hơn {@code date}.
 * @param categoryCode Mã (code) danh mục chi tiêu (bắt buộc đối với giao dịch EXPENSE, bỏ trống nếu CONTRIBUTION).
 * @param transactorId ID thành viên thực hiện / đối ứng giao dịch (người chi trả / người nộp quỹ).
 * @param note         Ghi chú hoặc mô tả nội dung giao dịch (tối đa 255 ký tự).
 * @param participants Danh sách phân bổ chi phí cho các thành viên.
 *                     Để null thì tức là không chia cho ai cả
 */
public record GroupTxnCreateReq(
        @NotNull(message = "chưa có loại giao dịch")
        GroupTxnType type,

        @NotNull(message = "phải chỉ định nguồn tiền")
        MoneySource moneySource,

        @NotNull(message = "Số tiền không được để trống.")
        @Positive(message = "Số tiền phải lớn hơn 0.")
        @Max(value = 999999999999L, message = "Số tiền vượt quá giới hạn cho phép.")
        Long amount,

        @NotNull(message = "Phải có thời gian giao dịch")
        @PastOrPresent(message = "Thời gian giao dịch không được ở tương lai.")
        Instant occurredAt,

        String categoryCode,
        UUID transactorId,

        @Size(max = 255, message = "Ghi chú không được vượt quá 255 ký tự.")
        String note,

        @NotEmpty(message = "Phải có ít nhất 1 người tham gia giao dịch.")
        List<@Valid GroupTxnParticipantReq> participants) {
}

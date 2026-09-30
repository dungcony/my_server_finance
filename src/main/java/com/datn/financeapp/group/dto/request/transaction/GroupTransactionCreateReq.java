package com.datn.financeapp.group.dto.request.transaction;

import com.datn.financeapp.group.enums.TransactionType;
import com.datn.financeapp.group.enums.MoneySource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
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
 *                     Nếu để trống, sẽ tự động suy luận qua {@link #resolveMoneySource()}.
 * @param amount       Số tiền giao dịch (bắt buộc, > 0, tối đa 999.999.999.999 VNĐ).
 * @param occurredAt   Thời điểm xảy ra giao dịch (UTC Instant / ISO-8601). Ưu tiên cao hơn {@code date}.
 * @param date         Ngày xảy ra giao dịch (YYYY-MM-DD), dùng khi client chỉ gửi ngày mà không có giờ.
 * @param categoryId   ID danh mục chi tiêu (bắt buộc đối với giao dịch EXPENSE, bỏ trống nếu CONTRIBUTION).
 * @param transactorId ID thành viên thực hiện / đối ứng giao dịch (người chi trả / người nộp quỹ).
 * @param note         Ghi chú hoặc mô tả nội dung giao dịch (tối đa 255 ký tự).
 * @param participants Danh sách phân bổ chi phí cho các thành viên (chỉ áp dụng cho EXPENSE).
 *                     Nếu để trống, hệ thống sẽ chia đều cho toàn bộ thành viên tại thời điểm giao dịch.
 */
public record GroupTransactionCreateReq(
        @NotNull(message = "chưa có loại giao dịch") TransactionType type,
        @NotNull(message = "phải chỉ định nguồn tiền") MoneySource moneySource,
        @NotNull @Positive @Max(999999999999L) Long amount,
        Instant occurredAt,
        LocalDate date,
        UUID categoryId,
        @NotNull(message = "Người thực hiện không được để trống") UUID transactorId,
        @Size(max = 255) String note,
        @Valid List<GroupTransactionParticipantReq> participants) {

    public GroupTransactionCreateReq {
        participants = participants == null
                ? Collections.emptyList()
                : participants;
    }

    /**
     * Xác định thời điểm xảy ra giao dịch chính xác.
     * <ul>
     *     <li>Ưu tiên 1: Sử dụng {@code occurredAt} nếu được cung cấp.</li>
     *     <li>Ưu tiên 2: Nếu có {@code date}, chuyển thành đầu ngày (00:00:00) theo múi giờ Việt Nam (UTC+7).</li>
     *     <li>Mặc định: Lấy thời điểm hiện tại {@link Instant#now()}.</li>
     * </ul>
     *
     * @return Thời điểm giao dịch dạng {@link Instant}
     */
    public Instant resolveOccurredAt() {
        if (occurredAt != null) {
            return occurredAt;
        }
        if (date != null) {
            return date.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        }
        return Instant.now();
    }

    /**
     * Xác định nguồn tiền của giao dịch khi client không truyền tường minh.
     * <ul>
     *     <li>Nếu có {@code moneySource}, giữ nguyên giá trị được truyền.</li>
     *     <li>Nếu là {@link TransactionType#CONTRIBUTION} (nộp quỹ): Mặc định là {@link MoneySource#PERSONAL}.</li>
     *     <li>Các trường hợp còn lại (như EXPENSE): Mặc định là {@link MoneySource#FUND}.</li>
     * </ul>
     *
     * @return Nguồn tiền hợp lệ ({@link MoneySource})
     */
    public MoneySource resolveMoneySource() {
        if (moneySource != null) {
            return moneySource;
        }
        if (type == TransactionType.CONTRIBUTION) {
            return MoneySource.PERSONAL;
        }
        return MoneySource.FUND;
    }
}

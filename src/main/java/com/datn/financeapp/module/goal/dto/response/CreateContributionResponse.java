package com.datn.financeapp.module.goal.dto.response;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Thông tin phản hồi tạo đợt đóng góp/rút tiền tiết kiệm.
 *
 * @param contribution Thông tin lượt đóng góp
 * @param goal         Tiến độ mục tiêu tiết kiệm cập nhật
 * @param transaction  Thông tin giao dịch ví tự động sinh (null nếu tắt)
 * @param newBalance   Số dư ví cập nhật (null nếu không trích tiền từ ví)
 */
public record CreateContributionResponse(
        Contribution contribution, GoalProgress goal, TransactionSummary transaction, NewBalance newBalance) {

    /**
     * Thông tin đợt nộp/rút tiền.
     *
     * @param id              ID đợt đóng góp
     * @param amount          Số tiền nộp/rút (Đơn vị: VND)
     * @param contributedDate Ngày nộp/rút
     * @param transactionId   ID giao dịch liên quan
     */
    public record Contribution(UUID id, Long amount, LocalDate contributedDate, UUID transactionId) {
    }

    /**
     * Tiến độ hoàn thành mục tiêu tiết kiệm.
     *
     * @param savedAmount Tổng số tiền đã tích lũy (Đơn vị: VND)
     * @param progress    Tỷ lệ hoàn thành (VD: 0.80 = 80%)
     * @param status      Trạng thái mục tiêu (VD: {@code in_progress}, {@code completed})
     */
    public record GoalProgress(Long savedAmount, java.math.BigDecimal progress, String status) {
    }

    /**
     * Tóm tắt giao dịch tự động sinh.
     *
     * @param id     ID giao dịch
     * @param type   Loại giao dịch
     * @param amount Số tiền (Đơn vị: VND)
     */
    public record TransactionSummary(UUID id, String type, Long amount) {
    }

    /**
     * Số dư ví cập nhật.
     *
     * @param walletId ID ví
     * @param balance  Số dư mới (Đơn vị: VND)
     */
    public record NewBalance(UUID walletId, Long balance) {
    }
}

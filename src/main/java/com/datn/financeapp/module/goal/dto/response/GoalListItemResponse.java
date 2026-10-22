package com.datn.financeapp.module.goal.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Thông tin chi tiết một mục tiêu tiết kiệm trong danh sách.
 *
 * @param id                ID mục tiêu tiết kiệm
 * @param name              Tên mục tiêu tiết kiệm
 * @param targetAmount      Số tiền mục tiêu cần tích lũy (Đơn vị: VND)
 * @param savedAmount       Số tiền đã tích lũy hiện tại (Đơn vị: VND)
 * @param missingAmount     Số tiền còn thiếu để đạt mục tiêu (Đơn vị: VND)
 * @param progress          Tỷ lệ hoàn thành (VD: 0.65 = 65%)
 * @param progressLabel     Nhãn phần trăm định dạng hiển thị
 * @param targetDate        Ngày hạn dự kiến hoàn thành mục tiêu
 * @param daysRemaining     Số ngày còn lại đến hạn
 * @param status            Trạng thái mục tiêu ({@code in_progress}, {@code completed}, {@code cancelled})
 * @param icon              Biểu tượng đại diện
 * @param wallet            Thông tin tóm tắt ví liên quan
 * @param suggestion        Gợi ý mức tiết kiệm hàng tháng (nếu có ngày hạn)
 * @param contributionCount Số lần đã nộp/rút tiền
 */
public record GoalListItemResponse(
        UUID id,
        String name,
        Long targetAmount,
        Long savedAmount,
        Long missingAmount,
        BigDecimal progress,
        String progressLabel,
        LocalDate targetDate,
        Integer daysRemaining,
        String status,
        IconSummary icon,
        WalletSummary wallet,
        Suggestion suggestion,
        int contributionCount) {

    /**
     * Tóm tắt biểu tượng.
     *
     * @param code     Mã biểu tượng
     * @param pathData Dữ liệu SVG/Vector
     */
    public record IconSummary(String code, String pathData) {
    }

    /**
     * Tóm tắt ví liên quan.
     *
     * @param id   ID ví
     * @param name Tên ví
     */
    public record WalletSummary(UUID id, String name) {
    }

    /**
     * Gợi ý kế hoạch tiết kiệm hàng tháng.
     *
     * @param monthlyRequired Số tiền cần tiết kiệm mỗi tháng (Đơn vị: VND)
     * @param content         Nội dung gợi ý
     * @param assessment      Đánh giá mức độ khả thi
     */
    public record Suggestion(Long monthlyRequired, String content, String assessment) {
    }
}

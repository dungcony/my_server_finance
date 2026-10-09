package com.datn.financeapp.module.budget.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Dữ liệu chi tiết/danh sách ngân sách chi tiêu.
 *
 * @param id            ID ngân sách
 * @param category      Thông tin tóm tắt danh mục chi tiêu
 * @param wallet        Thông tin tóm tắt ví áp dụng (null nếu áp dụng tất cả)
 * @param limitAmount   Hạn mức chi tiêu tối đa (Đơn vị: VND)
 * @param spentAmount   Số tiền đã chi trong kỳ (Đơn vị: VND)
 * @param remaining     Số tiền còn lại có thể chi (Đơn vị: VND)
 * @param ratio         Tỷ lệ đã chi so với hạn mức (VD: 0.75 = 75%)
 * @param percentLabel  Phần trăm chi tiêu định dạng số nguyên (VD: 75)
 * @param status        Trạng thái tiến độ ({@code normal}, {@code near_limit}, {@code over_limit})
 * @param statusColor   Mã màu đại diện cho trạng thái
 * @param periodType    Loại kỳ ngân sách ({@code week}, {@code month}, {@code quarter}, {@code year})
 * @param startDate     Ngày bắt đầu kỳ
 * @param endDate       Ngày kết thúc kỳ
 * @param daysRemaining Số ngày còn lại trong kỳ
 * @param autoRenew     Có tự động gia hạn khi hết kỳ hay không
 * @param isShared      Có phải ngân sách dùng chung nhóm không
 * @param alert         Thông tin cảnh báo kèm theo (nếu có)
 */
public record BudgetListItemResponse(
        UUID id,
        CategorySummary category,
        WalletSummary wallet,
        Long limitAmount,
        Long spentAmount,
        Long remaining,
        BigDecimal ratio,
        Integer percentLabel,
        String status,
        String statusColor,
        String periodType,
        LocalDate startDate,
        LocalDate endDate,
        Integer daysRemaining,
        Boolean autoRenew,
        boolean isShared,
        Alert alert) {

    /**
     * Thông tin tóm tắt danh mục chi tiêu.
     *
     * @param id    ID danh mục
     * @param name  Tên danh mục
     * @param icon  Biểu tượng danh mục
     * @param color Màu đại diện
     */
    public record CategorySummary(UUID id, String name, IconSummary icon, String color) {
    }

    /**
     * Thông tin biểu tượng.
     *
     * @param code     Mã biểu tượng
     * @param pathData Dữ liệu đường dẫn SVG/Vector
     */
    public record IconSummary(String code, String pathData) {
    }

    /**
     * Thông tin tóm tắt ví tiền áp dụng.
     *
     * @param id   ID ví
     * @param name Tên ví
     */
    public record WalletSummary(UUID id, String name) {
    }

    /**
     * Khối cảnh báo hiển thị kèm ngân sách.
     *
     * @param title                  Tiêu đề cảnh báo
     * @param content                Nội dung cảnh báo
     * @param projectedDepletionDate Ngày dự báo sẽ hết hạn mức (nếu có)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Alert(String title, String content, LocalDate projectedDepletionDate) {
    }
}

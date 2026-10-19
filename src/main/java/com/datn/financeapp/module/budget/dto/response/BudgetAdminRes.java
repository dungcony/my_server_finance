package com.datn.financeapp.module.budget.dto.response;

import com.datn.financeapp.module.budget.enums.BudgetStatus;
import com.datn.financeapp.module.budget.enums.PeriodType;
import com.datn.financeapp.module.category.dto.response.CateSumaryRes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO chi tiết ngân sách phục vụ cho màn hình Quản trị viên (Admin).
 * Bao gồm cả thông tin người sở hữu (userId) và metadata kiểm toán (audit).
 *
 * @param id             ID ngân sách
 * @param userId         ID người dùng sở hữu ngân sách
 * @param categoryCode   Mã danh mục
 * @param category       Thông tin tóm tắt danh mục (tên, icon, color)
 * @param limitAmount    Hạn mức chi tiêu tối đa (VND)
 * @param spentAmount    Số tiền thực tế đã chi tiêu trong kỳ (VND)
 * @param remaining      Số tiền còn lại có thể chi (có thể âm nếu chi vượt)
 * @param ratio          Tỉ lệ đã chi trên hạn mức (0.0000 - n.nnnn)
 * @param status         Trạng thái chi tiêu (NORMAL, NEAR_LIMIT, OVER_LIMIT)
 * @param periodType     Loại kỳ ngân sách (WEEK, MONTH, QUARTER, YEAR)
 * @param startDate      Ngày bắt đầu kỳ
 * @param endDate        Ngày kết thúc kỳ
 * @param daysRemaining  Số ngày còn lại của kỳ tính tới hết ngày endDate
 * @param autoRenew      Có tự động gia hạn sang kỳ kế tiếp hay không
 * @param isActive       Trạng thái đang theo dõi (true) hay tạm dừng (false)
 * @param isDeleted      Cờ xóa mềm trong CSDL
 * @param createdAt      Thời điểm tạo bản ghi
 * @param updatedAt      Thời điểm cập nhật bản ghi gần nhất
 */
public record BudgetAdminRes(
        UUID id,
        UUID userId,
        String categoryCode,
        CateSumaryRes category,
        long limitAmount,
        long spentAmount,
        long remaining,
        BigDecimal ratio,
        BudgetStatus status,
        PeriodType periodType,
        LocalDate startDate,
        LocalDate endDate,
        int daysRemaining,
        boolean autoRenew,
        boolean isActive,
        boolean isDeleted,
        Instant createdAt,
        Instant updatedAt
) {
}

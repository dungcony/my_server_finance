package com.datn.financeapp.module.budget.service;

import com.datn.financeapp.module.budget.dto.response.BudgetAdminRes;
import com.datn.financeapp.module.budget.enums.PeriodType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Giao diện dịch vụ quản trị ngân sách dành cho Quản trị viên (Admin).
 */
public interface BudgetAdminService {

    /**
     * Tra cứu và phân trang danh sách ngân sách của toàn hệ thống theo bộ lọc linh hoạt.
     *
     * @param targetUserId ID người dùng cần lọc (tùy chọn, null = toàn bộ người dùng)
     * @param isDeleted    Lọc theo trạng thái đã xóa mềm hay chưa (tùy chọn)
     * @param isActive     Lọc theo trạng thái kích hoạt (tùy chọn)
     * @param periodType   Lọc theo loại kỳ ngân sách (tùy chọn)
     * @param pageable     Thông tin phân trang và sắp xếp
     * @return Trang danh sách ngân sách
     */
    Page<BudgetAdminRes> list(
            UUID targetUserId,
            Boolean isDeleted,
            Boolean isActive,
            PeriodType periodType,
            Pageable pageable);

    /**
     * Xem chi tiết ngân sách bất kỳ theo ID (bỏ qua kiểm tra quyền sở hữu).
     *
     * @param budgetId ID ngân sách cần xem
     * @return DTO chi tiết ngân sách
     */
    BudgetAdminRes detail(UUID budgetId);

    /**
     * Quản trị viên cưỡng chế xóa mềm hoặc vô hiệu hóa ngân sách.
     *
     * @param budgetId ID ngân sách cần xóa
     */
    void forceDelete(UUID budgetId);
}

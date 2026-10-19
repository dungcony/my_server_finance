package com.datn.financeapp.module.budget.controller;

import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.module.budget.dto.response.BudgetAdminRes;
import com.datn.financeapp.module.budget.enums.PeriodType;
import com.datn.financeapp.module.budget.service.BudgetAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST Controller tiếp nhận các yêu cầu quản trị hệ thống liên quan đến ngân sách (dành riêng cho Quản trị viên).
 *
 * <p><b>Các endpoint chính:</b>
 * <ul>
 *   <li>{@link #list}: {@code GET /budgets/admin} - Tra cứu và phân trang ngân sách toàn hệ thống theo bộ lọc</li>
 *   <li>{@link #detail}: {@code GET /budgets/admin/{id}} - Xem chi tiết một ngân sách bất kỳ theo ID</li>
 *   <li>{@link #forceDelete}: {@code DELETE /budgets/admin/{id}} - Cưỡng chế xóa hoặc vô hiệu hóa ngân sách vi phạm</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/budgets/admin")
@RequiredArgsConstructor
public class BudgetAdminController {

    private final BudgetAdminService budgetAdminService;

    private static final String PERM_READ = "hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).BUDGET_READ.name()) or hasAnyAuthority('ROLE_ADMIN', 'ADMIN')";
    private static final String PERM_DELETE = "hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).BUDGET_DELETE.name()) or hasAnyAuthority('ROLE_ADMIN', 'ADMIN')";

    /**
     * Tra cứu danh sách ngân sách toàn hệ thống có phân trang và lọc theo điều kiện.
     *
     * @param targetUserId Lọc theo ID người dùng sở hữu (tùy chọn)
     * @param isDeleted    Lọc theo cờ xóa mềm (tùy chọn)
     * @param isActive     Lọc theo trạng thái hiệu lực (tùy chọn)
     * @param period       Lọc theo loại kỳ ngân sách (WEEK, MONTH, QUARTER, YEAR)
     * @param pageable     Thông tin phân trang (page, size, sort)
     */
    @PreAuthorize(PERM_READ)
    @GetMapping
    public ApiResponse<Page<BudgetAdminRes>> list(
            @RequestParam(name = "user_id", required = false) UUID targetUserId,
            @RequestParam(name = "is_deleted", required = false) Boolean isDeleted,
            @RequestParam(name = "is_active", required = false) Boolean isActive,
            @RequestParam(name = "period", required = false) String period,
            Pageable pageable) {

        PeriodType periodType = period == null || period.isBlank() ? null : PeriodType.fromCode(period);
        return ApiResponse.of(budgetAdminService.list(targetUserId, isDeleted, isActive, periodType, pageable));
    }

    /**
     * Xem chi tiết ngân sách bất kỳ theo ID (bỏ qua kiểm tra quyền sở hữu).
     *
     * @param id ID ngân sách cần xem
     */
    @PreAuthorize(PERM_READ)
    @GetMapping("/{id}")
    public ApiResponse<BudgetAdminRes> detail(@PathVariable UUID id) {
        return ApiResponse.of(budgetAdminService.detail(id));
    }

    /**
     * Cưỡng chế xóa mềm hoặc vô hiệu hóa ngân sách của một người dùng.
     *
     * @param id ID ngân sách cần xóa
     */
    @PreAuthorize(PERM_DELETE)
    @DeleteMapping("/{id}")
    public ApiResponse<Void> forceDelete(@PathVariable UUID id) {
        budgetAdminService.forceDelete(id);
        return ApiResponse.of(null);
    }
}

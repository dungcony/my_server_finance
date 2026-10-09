package com.datn.financeapp.module.budget.controller;

import com.datn.financeapp.module.budget.dto.response.BudgetAlertResponse;
import com.datn.financeapp.module.budget.dto.response.BudgetListItemResponse;
import com.datn.financeapp.module.budget.dto.response.BudgetSuggestionResponse;
import com.datn.financeapp.module.budget.dto.response.BudgetSummaryResponse;
import com.datn.financeapp.module.budget.dto.request.CreateBudgetRequest;
import com.datn.financeapp.module.budget.dto.request.UpdateBudgetRequest;
import com.datn.financeapp.module.budget.service.BudgetService;
import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.security.SecurityContextUtil;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller tiếp nhận các yêu cầu HTTP quản lý ngân sách chi tiêu.
 * <p>
 * <b>Các endpoint chính:</b>
 * <ul>
 *   <li>{@link #list(Boolean, String)}: {@code GET /budgets} - Truy vấn danh sách ngân sách</li>
 *   <li>{@link #summary()}: {@code GET /budgets/summary} - Báo cáo tổng quan ngân sách</li>
 *   <li>{@link #suggestion(UUID)}: {@code GET /budgets/suggestion} - Xin gợi ý hạn mức</li>
 *   <li>{@link #alerts()}: {@code GET /budgets/alerts} - Danh sách cảnh báo ngân sách</li>
 *   <li>{@link #detail(UUID)}: {@code GET /budgets/{id}} - Chi tiết ngân sách theo ID</li>
 *   <li>{@link #create(CreateBudgetRequest)}: {@code POST /budgets} - Tạo mới ngân sách</li>
 *   <li>{@link #update(UUID, UpdateBudgetRequest)}: {@code PATCH /budgets/{id}} - Cập nhật ngân sách</li>
 *   <li>{@link #delete(UUID)}: {@code DELETE /budgets/{id}} - Xóa (vô hiệu hóa) ngân sách</li>
 * </ul>
 * </p>
 *
 * @see BudgetService
 */
@RestController
@RequestMapping("/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    @GetMapping
    public ApiResponse<List<BudgetListItemResponse>> list(
            @RequestParam(name = "is_active", required = false, defaultValue = "true") Boolean isActive,
            @RequestParam(name = "period", required = false) String period) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(budgetService.list(userId, isActive, period));
    }

    @GetMapping("/summary")
    public ApiResponse<BudgetSummaryResponse> summary() {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(budgetService.summary(userId));
    }

    @GetMapping("/suggestion")
    public ApiResponse<BudgetSuggestionResponse> suggestion(@RequestParam(name = "category_id") UUID categoryId) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(budgetService.suggestion(userId, categoryId));
    }

    @GetMapping("/alerts")
    public ApiResponse<List<BudgetAlertResponse>> alerts() {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(budgetService.alerts(userId));
    }

    @GetMapping("/{id}")
    public ApiResponse<BudgetListItemResponse> detail(@PathVariable UUID id) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(budgetService.detail(userId, id));
    }

    @PostMapping
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BudgetListItemResponse> create(@Valid @RequestBody CreateBudgetRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(budgetService.create(userId, req));
    }

    @PatchMapping("/{id}")
    public ApiResponse<BudgetListItemResponse> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateBudgetRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(budgetService.update(userId, id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        UUID userId = SecurityContextUtil.currentUserId();
        budgetService.delete(userId, id);
        return ApiResponse.of(null);
    }
}

package com.datn.financeapp.budget.controller;

import com.datn.financeapp.budget.dto.response.BudgetAlertResponse;
import com.datn.financeapp.budget.dto.response.BudgetListItemResponse;
import com.datn.financeapp.budget.dto.response.BudgetSuggestionResponse;
import com.datn.financeapp.budget.dto.response.BudgetSummaryResponse;
import com.datn.financeapp.budget.dto.request.CreateBudgetRequest;
import com.datn.financeapp.budget.dto.request.UpdateBudgetRequest;
import com.datn.financeapp.budget.service.BudgetService;
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
 * 8 endpoint ngân sách (BUDGET-01..06, api/05-NGAN-SACH.md bảng "Danh sách điểm cuối").
 *
 * <p>Chỉ {@code POST /budgets} gắn {@link Idempotent} theo D-11 (chỉ POST-tạo-mới). PATCH là ghi
 * đè nên tự idempotent về mặt kết quả; DELETE tắt {@code is_active} gọi lại nhiều lần cũng không
 * đổi gì thêm.
 *
 * <p><b>Thứ tự khai báo route quan trọng:</b> {@code /budgets/summary}, {@code /budgets/suggestion}
 * và {@code /budgets/alerts} phải đứng TRƯỚC {@code /budgets/{id}} — nếu không Spring sẽ khớp
 * "summary" vào {@code {id}} rồi ném lỗi ép kiểu UUID.
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

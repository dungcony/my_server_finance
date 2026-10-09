package com.datn.financeapp.module.goal.controller;

import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.goal.dto.request.CreateContributionRequest;
import com.datn.financeapp.module.goal.dto.response.CreateContributionResponse;
import com.datn.financeapp.module.goal.dto.request.CreateGoalRequest;
import com.datn.financeapp.module.goal.dto.response.CreateGoalResponse;
import com.datn.financeapp.module.goal.dto.response.GoalDetailResponse;
import com.datn.financeapp.module.goal.dto.response.GoalListItemResponse;
import com.datn.financeapp.module.goal.dto.request.UpdateGoalRequest;
import com.datn.financeapp.module.goal.service.GoalService;
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
 * Controller tiếp nhận các yêu cầu HTTP quản lý mục tiêu tiết kiệm.
 * <p>
 * <b>Các endpoint chính:</b>
 * <ul>
 *   <li>{@link #list(String)}: {@code GET /goals} - Danh sách mục tiêu tiết kiệm</li>
 *   <li>{@link #detail(UUID)}: {@code GET /goals/{id}} - Chi tiết mục tiêu tiết kiệm</li>
 *   <li>{@link #create(CreateGoalRequest)}: {@code POST /goals} - Tạo mới mục tiêu tiết kiệm</li>
 *   <li>{@link #update(UUID, UpdateGoalRequest)}: {@code PATCH /goals/{id}} - Cập nhật mục tiêu tiết kiệm</li>
 *   <li>{@link #delete(UUID, boolean)}: {@code DELETE /goals/{id}} - Xóa mục tiêu tiết kiệm</li>
 *   <li>{@link #addContribution(UUID, CreateContributionRequest)}: {@code POST /goals/{id}/contributions} - Đóng góp/rút tiền</li>
 *   <li>{@link #cancelContribution(UUID, UUID)}: {@code DELETE /goals/{id}/contributions/{contributionId}} - Hủy đợt đóng góp</li>
 * </ul>
 * </p>
 *
 * @see GoalService
 */
@RestController
@RequestMapping("/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    @GetMapping
    public ApiResponse<List<GoalListItemResponse>> list(
            @RequestParam(name = "status", required = false) String status) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(goalService.list(userId, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<GoalDetailResponse> detail(@PathVariable UUID id) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(goalService.detail(userId, id));
    }

    @PostMapping
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateGoalResponse> create(@Valid @RequestBody CreateGoalRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(goalService.create(userId, req));
    }

    @PatchMapping("/{id}")
    public ApiResponse<GoalListItemResponse> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateGoalRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(goalService.update(userId, id, req));
    }

    /**
     * api/09 mục B5 — {@code revert_transactions} mặc định {@code false}: tiền đã chuyển vào ví
     * tiết kiệm thì vẫn nằm đó, chỉ là không theo dõi mục tiêu nữa.
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable UUID id,
            @RequestParam(name = "revert_transactions", required = false, defaultValue = "false")
            boolean revertTransactions) {
        UUID userId = SecurityContextUtil.currentUserId();
        goalService.delete(userId, id, revertTransactions);
        return ApiResponse.of(null);
    }

    @PostMapping("/{id}/contributions")
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateContributionResponse> addContribution(
            @PathVariable UUID id, @Valid @RequestBody CreateContributionRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(goalService.addContribution(userId, id, req));
    }

    @DeleteMapping("/{id}/contributions/{contributionId}")
    public ApiResponse<Void> cancelContribution(@PathVariable UUID id, @PathVariable UUID contributionId) {
        UUID userId = SecurityContextUtil.currentUserId();
        goalService.cancelContribution(userId, id, contributionId);
        return ApiResponse.of(null);
    }
}

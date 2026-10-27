package com.datn.financeapp.module.goal.controller;

import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.module.goal.dto.request.CreateContributionRequest;
import com.datn.financeapp.module.goal.dto.request.CreateGoalReq;
import com.datn.financeapp.module.goal.dto.request.UpdateContributionRequest;
import com.datn.financeapp.module.goal.dto.request.UpdateGoalRequest;
import com.datn.financeapp.module.goal.dto.request.WithdrawGoalRequest;
import com.datn.financeapp.module.goal.dto.response.CreateContributionResponse;
import com.datn.financeapp.module.goal.dto.response.CreateGoalResponse;
import com.datn.financeapp.module.goal.dto.response.GoalRes;
import com.datn.financeapp.module.goal.dto.response.GoalListItemResponse;
import com.datn.financeapp.module.goal.service.GoalAdminService;
import com.datn.financeapp.module.goal.service.GoalBehavierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller tiếp nhận các yêu cầu HTTP quản lý mục tiêu tiết kiệm.
 *
 * <p>Cung cấp đầy đủ API cho cả người dùng cá nhân (theo dõi mục tiêu, nạp tiền, rút tiền, sửa/xóa khoản góp)
 * và ban quản trị (Admin).</p>
 *
 * @see GoalBehavierService
 * @see GoalAdminService
 */
@RestController
@RequestMapping("/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalBehavierService goalBehavierService;

    @GetMapping
    public ApiResponse<List<GoalListItemResponse>> list(
            @RequestParam(name = "status", required = false) String status) {
        return ApiResponse.of(goalBehavierService.list(userId, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<GoalRes> detail(@PathVariable UUID id) {
        return ApiResponse.of(goalBehavierService.detail(userId, id));
    }

    @PostMapping
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateGoalResponse> create(@Valid @RequestBody CreateGoalReq req) {
        return ApiResponse.of(goalBehavierService.create(userId, req));
    }

    @PatchMapping("/{id}")
    public ApiResponse<GoalListItemResponse> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateGoalRequest req) {
        return ApiResponse.of(goalBehavierService.update(userId, id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        goalBehavierService.delete(userId, id);
        return ApiResponse.of(null);
    }

    @PostMapping("/{id}/contributions")
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateContributionResponse> addContribution(
            @PathVariable UUID id, @Valid @RequestBody CreateContributionRequest req) {
        return ApiResponse.of(goalBehavierService.addContribution(userId, id, req));
    }

    @PostMapping("/{id}/withdraw")
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateContributionResponse> withdraw(
            @PathVariable UUID id, @Valid @RequestBody WithdrawGoalRequest req) {
        return ApiResponse.of(goalBehavierService.withdraw(userId, id, req));
    }

    @PatchMapping("/{id}/contributions/{contributionId}")
    public ApiResponse<CreateContributionResponse> updateContribution(
            @PathVariable UUID id,
            @PathVariable UUID contributionId,
            @Valid @RequestBody UpdateContributionRequest req) {
        return ApiResponse.of(goalBehavierService.updateContribution(userId, id, contributionId, req));
    }

    @DeleteMapping("/{id}/contributions/{contributionId}")
    public ApiResponse<Void> cancelContribution(@PathVariable UUID id, @PathVariable UUID contributionId) {
        goalBehavierService.cancelContribution(userId, id, contributionId);
        return ApiResponse.of(null);
    }

}

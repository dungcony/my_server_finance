package com.datn.financeapp.module.goal.controller;

import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.module.goal.dto.request.*;
import com.datn.financeapp.module.goal.dto.response.CreateContributionResponse;
import com.datn.financeapp.module.goal.dto.response.CreateGoalResponse;
import com.datn.financeapp.module.goal.dto.response.GoalRes;
import com.datn.financeapp.module.goal.dto.response.GoalListItemResponse;
import com.datn.financeapp.module.goal.service.GoalAdminService;
import com.datn.financeapp.module.goal.service.GoalBehavierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/goals")
@RequiredArgsConstructor
public class GoalAdminController {

    private final GoalBehavierService goalBehavierService;
    private final GoalAdminService goalAdminService;

    private static final String MANAGE_GOAL_READ =
            "hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).GOAL_READ.name())";
    private static final String MANAGE_GOAL_UPDATE =
            "hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).GOAL_UPDATE.name())";
    private static final String MANAGE_GOAL_DELETE =
            "hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).GOAL_DELETE.name())";


    @PreAuthorize(MANAGE_GOAL_READ)
    @GetMapping("/admin/all")
    public ApiResponse<Page<GoalListItemResponse>> adminList(
            @ModelAttribute GoalFilterRequest filter,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.of(goalAdminService.list(filter, pageable));
    }

    @PreAuthorize(MANAGE_GOAL_READ)
    @GetMapping("/admin/detail/{id}")
    public ApiResponse<GoalRes> adminDetail(@PathVariable UUID id) {
        return ApiResponse.of(goalAdminService.detail(id));
    }

    @PreAuthorize(MANAGE_GOAL_UPDATE)
    @PostMapping("/admin/{userId}")
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateGoalResponse> adminCreate(
            @PathVariable UUID userId,
            @Valid @RequestBody CreateGoalReq req) {
        return ApiResponse.of(goalAdminService.create(userId, req));
    }

    @PreAuthorize(MANAGE_GOAL_UPDATE)
    @PatchMapping("/admin/{id}")
    public ApiResponse<GoalListItemResponse> adminUpdate(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateGoalRequest req) {
        return ApiResponse.of(goalAdminService.update(id, req));
    }

    @PreAuthorize(MANAGE_GOAL_UPDATE)
    @PostMapping("/admin/{id}/contributions")
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateContributionResponse> adminAddContribution(
            @PathVariable UUID id,
            @Valid @RequestBody CreateContributionRequest req) {
        return ApiResponse.of(goalAdminService.addContribution(id, req));
    }

    @PreAuthorize(MANAGE_GOAL_UPDATE)
    @PostMapping("/admin/{id}/withdraw")
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateContributionResponse> adminWithdraw(
            @PathVariable UUID id,
            @Valid @RequestBody WithdrawGoalRequest req) {
        return ApiResponse.of(goalAdminService.withdraw(id, req));
    }

    @PreAuthorize(MANAGE_GOAL_UPDATE)
    @PatchMapping("/admin/{id}/contributions/{contributionId}")
    public ApiResponse<CreateContributionResponse> adminUpdateContribution(
            @PathVariable UUID id,
            @PathVariable UUID contributionId,
            @Valid @RequestBody UpdateContributionRequest req) {
        return ApiResponse.of(goalAdminService.updateContribution(id, contributionId, req));
    }

    @PreAuthorize(MANAGE_GOAL_DELETE)
    @DeleteMapping("/admin/{id}/contributions/{contributionId}")
    public ApiResponse<Void> adminCancelContribution(
            @PathVariable UUID id,
            @PathVariable UUID contributionId) {
        goalAdminService.cancelContribution(id, contributionId);
        return ApiResponse.of(null);
    }

    @PreAuthorize(MANAGE_GOAL_DELETE)
    @DeleteMapping("/admin/{id}")
    public ApiResponse<Void> adminDelete(@PathVariable UUID id) {
        goalAdminService.delete(id);
        return ApiResponse.of(null);
    }
}

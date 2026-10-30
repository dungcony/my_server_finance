package com.datn.financeapp.module.goal.controller;

import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.module.goal.dto.request.ContributionGoalCreateReq;
import com.datn.financeapp.module.goal.dto.request.ContributionGoalUpdateReq;
import com.datn.financeapp.module.goal.dto.request.SavingGoalCreateReq;
import com.datn.financeapp.module.goal.dto.request.SavingGoalFilterReq;
import com.datn.financeapp.module.goal.dto.request.SavingGoalUpdateRequest;
import com.datn.financeapp.module.goal.dto.response.ContributionGoalRes;
import com.datn.financeapp.module.goal.dto.response.SavingGoalListRes;
import com.datn.financeapp.module.goal.dto.response.SavingGoalRes;
import com.datn.financeapp.module.goal.service.ContributionBehaviorService;
import com.datn.financeapp.module.goal.service.SavingBehavierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller tiếp nhận các yêu cầu HTTP quản lý mục tiêu tiết kiệm dành cho Quản trị viên (Admin).
 */
@RestController
@RequestMapping("/admin/goals")
@RequiredArgsConstructor
public class SavingGoalAdminController {

    private final SavingBehavierService savingBehavierService;

    private static final String MANAGE_GOAL_READ =
            "hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).GOAL_READ.name())";
    private static final String MANAGE_GOAL_UPDATE =
            "hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).GOAL_UPDATE.name())";
    private static final String MANAGE_GOAL_DELETE =
            "hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).GOAL_DELETE.name())";

    @PreAuthorize(MANAGE_GOAL_READ)
    @GetMapping("/all")
    public ApiResponse<SavingGoalListRes> adminList(
            @ModelAttribute SavingGoalFilterReq filter,
            Pageable pageable) {
        return ApiResponse.of(savingBehavierService.gets(null, filter, pageable));
    }

    @PreAuthorize(MANAGE_GOAL_READ)
    @GetMapping("/{user_id}")
    public ApiResponse<SavingGoalListRes> adminList(
            @PathVariable UUID user_id,
            @ModelAttribute SavingGoalFilterReq filter,
            Pageable pageable) {
        return ApiResponse.of(savingBehavierService.gets(user_id, filter, pageable));
    }

    @PreAuthorize(MANAGE_GOAL_READ)
    @GetMapping("/detail/{id}")
    public ApiResponse<SavingGoalRes> adminDetail(@PathVariable UUID id) {
        return ApiResponse.of(savingBehavierService.detail(id, true));
    }

    @PreAuthorize(MANAGE_GOAL_UPDATE)
    @PostMapping("/{userId}")
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SavingGoalRes> adminCreate(
            @PathVariable UUID userId,
            @Valid @RequestBody SavingGoalCreateReq req) {
        return ApiResponse.of(savingBehavierService.create(userId, req));
    }

    @PreAuthorize(MANAGE_GOAL_UPDATE)
    @PatchMapping("/{id}")
    public ApiResponse<SavingGoalRes> adminUpdate(
            @PathVariable UUID id,
            @Valid @RequestBody SavingGoalUpdateRequest req) {
        return ApiResponse.of(savingBehavierService.update(id, req, true));
    }

    @PreAuthorize(MANAGE_GOAL_DELETE)
    @DeleteMapping("/{id}")
    public ApiResponse<Void> adminDelete(@PathVariable UUID id) {
        savingBehavierService.delete(id, true);
        return ApiResponse.of(null);
    }
}

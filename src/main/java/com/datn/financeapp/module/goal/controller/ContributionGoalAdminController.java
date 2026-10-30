package com.datn.financeapp.module.goal.controller;

import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.module.goal.dto.request.ContributionGoalCreateReq;
import com.datn.financeapp.module.goal.dto.request.ContributionGoalFilterReq;
import com.datn.financeapp.module.goal.dto.request.ContributionGoalUpdateReq;
import com.datn.financeapp.module.goal.dto.response.ContributionGoalListRes;
import com.datn.financeapp.module.goal.dto.response.ContributionGoalRes;
import com.datn.financeapp.module.goal.service.ContributionBehaviorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin/goals/{id}")
@RequiredArgsConstructor
public class ContributionGoalAdminController {

    private final ContributionBehaviorService contributionBehaviorService;

    private static final String MANAGE_GOAL_READ =
            "hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).GOAL_READ.name())";
    private static final String MANAGE_GOAL_UPDATE =
            "hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).GOAL_UPDATE.name())";
    private static final String MANAGE_GOAL_DELETE =
            "hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).GOAL_DELETE.name())";

    @PreAuthorize(MANAGE_GOAL_UPDATE)
    @PostMapping("/contributions")
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ContributionGoalRes> adminAddContribution(
            @PathVariable UUID id,
            @Valid @RequestBody ContributionGoalCreateReq req) {
        return ApiResponse.of(contributionBehaviorService.addContribution(id, req, true));
    }

    @PreAuthorize(MANAGE_GOAL_READ)
    @GetMapping("/contributions")
    public ApiResponse<ContributionGoalListRes> getContributions(
            @PathVariable UUID id,
            @ModelAttribute ContributionGoalFilterReq filter,
            Pageable pageable) {
        return ApiResponse.of(contributionBehaviorService.gets(id, filter, pageable, true));
    }

    @PreAuthorize(MANAGE_GOAL_UPDATE)
    @PatchMapping("/contributions/{contributionId}")
    public ApiResponse<ContributionGoalRes> adminUpdateContribution(
            @PathVariable UUID id,
            @PathVariable UUID contributionId,
            @Valid @RequestBody ContributionGoalUpdateReq req) {
        return ApiResponse.of(contributionBehaviorService.updateContribution(id, contributionId, req, true));
    }

    @PreAuthorize(MANAGE_GOAL_DELETE)
    @DeleteMapping("/contributions/{contributionId}")
    public ApiResponse<Void> adminCancelContribution(
            @PathVariable UUID id,
            @PathVariable UUID contributionId) {
        contributionBehaviorService.deleteContribution(id, contributionId, true);
        return ApiResponse.of(null);
    }
}

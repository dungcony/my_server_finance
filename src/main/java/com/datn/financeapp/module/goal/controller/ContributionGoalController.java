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
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/goals/{id}")
@RequiredArgsConstructor
public class ContributionGoalController {

    private final ContributionBehaviorService contributionBehaviorService;

    @PostMapping("/contributions")
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ContributionGoalRes> addContribution(
            @PathVariable UUID id, @Valid @RequestBody ContributionGoalCreateReq req) {
        return ApiResponse.of(contributionBehaviorService.addContribution(id, req, false));
    }

    @GetMapping("/contributions")
    public ApiResponse<ContributionGoalListRes> getContributions(
            @PathVariable UUID id,
            @ModelAttribute ContributionGoalFilterReq filter,
            Pageable pageable) {
        return ApiResponse.of(contributionBehaviorService.gets(id, filter, pageable, false));
    }
    
    @PatchMapping("/contributions/{contributionId}")
    public ApiResponse<ContributionGoalRes> updateContribution(
            @PathVariable UUID id,
            @PathVariable UUID contributionId,
            @Valid @RequestBody ContributionGoalUpdateReq req) {
        return ApiResponse.of(contributionBehaviorService.updateContribution(id, contributionId, req, false));
    }

    @DeleteMapping("/contributions/{contributionId}")
    public ApiResponse<Void> cancelContribution(@PathVariable UUID id, @PathVariable UUID contributionId) {
        contributionBehaviorService.deleteContribution(id, contributionId, false);
        return ApiResponse.of(null);
    }
}

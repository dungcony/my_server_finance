package com.datn.financeapp.module.goal.controller;

import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.module.goal.dto.request.*;
import com.datn.financeapp.module.goal.dto.response.ContributionGoalRes;
import com.datn.financeapp.module.goal.dto.response.SavingGoalListRes;
import com.datn.financeapp.module.goal.dto.response.SavingGoalRes;
import com.datn.financeapp.module.goal.service.ContributionBehaviorService;
import com.datn.financeapp.module.goal.service.SavingBehavierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller tiếp nhận các yêu cầu HTTP quản lý mục tiêu tiết kiệm dành cho người dùng cá nhân.
 */
@RestController
@RequestMapping("/goals")
@RequiredArgsConstructor
public class SavingGoalController {

    private final SavingBehavierService savingBehavierService;

    @GetMapping
    public ApiResponse<SavingGoalListRes> gets(
            @ModelAttribute SavingGoalFilterReq filter,
            Pageable pageable) {
        return ApiResponse.of(savingBehavierService.gets(filter, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<SavingGoalRes> detail(@PathVariable UUID id) {
        return ApiResponse.of(savingBehavierService.detail(id, false));
    }

    @PostMapping
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SavingGoalRes> create(@Valid @RequestBody SavingGoalCreateReq req) {
        return ApiResponse.of(savingBehavierService.create(req));
    }

    @PatchMapping("/{id}")
    public ApiResponse<SavingGoalRes> update(
            @PathVariable UUID id, @Valid @RequestBody SavingGoalUpdateRequest req) {
        return ApiResponse.of(savingBehavierService.update(id, req, false));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        savingBehavierService.delete(id, false);
        return ApiResponse.of(null);
    }

}

package com.datn.financeapp.module.transaction.controller;

import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.module.transaction.dto.request.RecCreateReq;
import com.datn.financeapp.module.transaction.dto.request.RecTxnScheduleCreateReq;
import com.datn.financeapp.module.transaction.dto.request.RecTxnScheduleUpdateReq;
import com.datn.financeapp.module.transaction.dto.request.RecUpdateReq;
import com.datn.financeapp.module.transaction.service.RecTxnBehavierService;
import com.datn.financeapp.module.transaction.service.RecTxnScheduleBehavierService;
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
 * Controller tiếp nhận các yêu cầu HTTP quản lý giao dịch định kỳ và các buổi lịch con.
 */
@RestController
@RequestMapping("/recurring")
@RequiredArgsConstructor
public class RecController {

    private final RecTxnBehavierService recTxnBehavierService;
    private final RecTxnScheduleBehavierService scheduleBehavierService;
    // luồng người dùng thông thường

    @GetMapping("/{id}")
    public ApiResponse<?> detail(@PathVariable UUID id) {
        return ApiResponse.of(recTxnBehavierService.detail(id, null));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<?> create(@Valid @RequestBody RecCreateReq req) {
        return ApiResponse.of(recTxnBehavierService.create(null, req));
    }

    @PatchMapping("/{id}")
    public ApiResponse<?> update(@PathVariable UUID id, @Valid @RequestBody RecUpdateReq req) {
        return ApiResponse.of(recTxnBehavierService.update(null, id, req));
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<?> enable(@PathVariable UUID id) {
        return ApiResponse.of(recTxnBehavierService.enable(null, id));
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<?> disable(@PathVariable UUID id) {
        return ApiResponse.of(recTxnBehavierService.disable(null, id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable UUID id) {
        return ApiResponse.of(recTxnBehavierService.delete(null, id));
    }

    // quản lý các buổi lịch phát sinh

    @GetMapping("/{recId}/schedules")
    public ApiResponse<?> getSchedules(
            @PathVariable UUID recId,
            @PageableDefault(size = 20, sort = "runAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return ApiResponse.of(scheduleBehavierService.gets(null, recId, pageable));
    }

    @PostMapping("/{recId}/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<?> createSchedule(
            @PathVariable UUID recId,
            @Valid @RequestBody RecTxnScheduleCreateReq req) {
        return ApiResponse.of(scheduleBehavierService.create(null, recId, req));
    }

    @PatchMapping("/schedules/{scheduleId}")
    public ApiResponse<?> updateSchedule(
            @PathVariable UUID scheduleId,
            @Valid @RequestBody RecTxnScheduleUpdateReq req) {
        return ApiResponse.of(scheduleBehavierService.update(null, scheduleId, req));
    }

    @DeleteMapping("/schedules/{scheduleId}")
    public ApiResponse<?> deleteSchedule(@PathVariable UUID scheduleId) {
        return ApiResponse.of(scheduleBehavierService.delete(null, scheduleId));
    }

    // luồng quản trị viên

    @PreAuthorize("hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).RECURRING_TXN_READ.getValue())")
    @GetMapping("/admin/{recId}/schedules")
    public ApiResponse<?> gets(
            @RequestParam(name = "user_id", required = false) UUID userId,
            @PathVariable UUID recId,
            Pageable pageable) {
        return ApiResponse.of(scheduleBehavierService.gets(userId, recId, pageable));
    }

    @PreAuthorize("hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).RECURRING_TXN_READ.getValue())")
    @GetMapping("/admin/{id}")
    public ApiResponse<?> adminDetail(
            @PathVariable UUID id,
            @RequestParam(name = "user_id", required = false) UUID userId) {
        return ApiResponse.of(recTxnBehavierService.detail(id, userId));
    }

    @PreAuthorize("hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).RECURRING_TXN_DELETE.getValue())")
    @DeleteMapping("/admin/{id}")
    public ApiResponse<?> adminDelete(
            @PathVariable UUID id,
            @RequestParam(name = "user_id") UUID userId) {
        return ApiResponse.of(recTxnBehavierService.delete(userId, id));
    }

    @PreAuthorize("hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).RECURRING_TXN_DELETE.getValue())")
    @DeleteMapping("/admin/schedules/{scheduleId}")
    public ApiResponse<?> adminDeleteSchedule(
            @PathVariable UUID scheduleId,
            @RequestParam(name = "user_id") UUID userId) {
        return ApiResponse.of(scheduleBehavierService.delete(userId, scheduleId));
    }
}
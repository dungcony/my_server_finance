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

    // ==========================================
    // LUỒNG NGƯỜI DÙNG THÔNG THƯỜNG
    // ==========================================

    @GetMapping("/{id}")
    public ApiResponse<?> detail(@PathVariable UUID id) {
        return ApiResponse.of(recTxnBehavierService.get(id, false));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<?> create(@Valid @RequestBody RecCreateReq req) {
        return ApiResponse.of(recTxnBehavierService.create(req, false));
    }

    @PatchMapping("/{id}")
    public ApiResponse<?> update(@PathVariable UUID id, @Valid @RequestBody RecUpdateReq req) {
        return ApiResponse.of(recTxnBehavierService.update(id, req, false));
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<?> enable(@PathVariable UUID id) {
        return ApiResponse.of(recTxnBehavierService.enable(id, false));
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<?> disable(@PathVariable UUID id) {
        return ApiResponse.of(recTxnBehavierService.disable(id, false));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable UUID id) {
        return ApiResponse.of(recTxnBehavierService.delete(id, false));
    }

    // ==========================================
    // QUẢN LÝ CÁC BUỔI LỊCH PHÁT SINH
    // ==========================================

    @GetMapping("/{recId}/schedules")
    public ApiResponse<?> getSchedules(
            @PathVariable UUID recId,
            @PageableDefault(size = 20, sort = "runDate", direction = Sort.Direction.ASC) Pageable pageable) {
        return ApiResponse.of(scheduleBehavierService.gets(recId, pageable, false));
    }

    @PostMapping("/{recId}/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<?> createSchedule(
            @PathVariable UUID recId,
            @Valid @RequestBody RecTxnScheduleCreateReq req) {
        return ApiResponse.of(scheduleBehavierService.create(recId, req, false));
    }

    @PatchMapping("/schedules/{scheduleId}")
    public ApiResponse<?> updateSchedule(
            @PathVariable UUID scheduleId,
            @Valid @RequestBody RecTxnScheduleUpdateReq req) {
        return ApiResponse.of(scheduleBehavierService.update(scheduleId, req, false));
    }

    @PostMapping("/schedules/{scheduleId}/skip")
    public ApiResponse<?> skipSchedule(@PathVariable UUID scheduleId) {
        return ApiResponse.of(scheduleBehavierService.skip(scheduleId, false));
    }

    @PostMapping("/schedules/{scheduleId}/unskip")
    public ApiResponse<?> unskipSchedule(@PathVariable UUID scheduleId) {
        return ApiResponse.of(scheduleBehavierService.unskip(scheduleId, false));
    }

    @DeleteMapping("/schedules/{scheduleId}")
    public ApiResponse<?> deleteSchedule(@PathVariable UUID scheduleId) {
        return ApiResponse.of(scheduleBehavierService.delete(scheduleId, false));
    }

    // ==========================================
    // LUỒNG QUẢN TRỊ VIÊN
    // ==========================================

    @PreAuthorize("hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).RECURRING_TXN_READ.getValue())")
    @GetMapping("/admin/{recId}/schedules")
    public ApiResponse<?> gets(
            @PathVariable UUID recId,
            Pageable pageable) {
        return ApiResponse.of(scheduleBehavierService.gets(recId, pageable, true));
    }

    @PreAuthorize("hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).RECURRING_TXN_READ.getValue())")
    @GetMapping("/admin/{id}")
    public ApiResponse<?> adminDetail(@PathVariable UUID id) {
        return ApiResponse.of(recTxnBehavierService.get(id, true));
    }

    @PreAuthorize("hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).RECURRING_TXN_DELETE.getValue())")
    @DeleteMapping("/admin/{id}")
    public ApiResponse<?> adminDelete(@PathVariable UUID id) {
        return ApiResponse.of(recTxnBehavierService.delete(id, true));
    }

    @PreAuthorize("hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).RECURRING_TXN_DELETE.getValue())")
    @DeleteMapping("/admin/schedules/{scheduleId}")
    public ApiResponse<?> adminDeleteSchedule(@PathVariable UUID scheduleId) {
        return ApiResponse.of(scheduleBehavierService.delete(scheduleId, true));
    }
}

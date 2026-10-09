package com.datn.financeapp.module.recurring.controller;

import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.recurring.dto.request.CreateRecurringRequest;
import com.datn.financeapp.module.recurring.dto.request.PauseRecurringRequest;
import com.datn.financeapp.module.recurring.dto.response.RecurringDetailResponse;
import com.datn.financeapp.module.recurring.dto.response.RecurringListItemResponse;
import com.datn.financeapp.module.recurring.dto.response.RunNowResponse;
import com.datn.financeapp.module.recurring.dto.request.UpdateRecurringRequest;
import com.datn.financeapp.module.recurring.service.RecurringService;
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
 * Controller tiếp nhận các yêu cầu HTTP quản lý giao dịch định kỳ.
 * <p>
 * <b>Các endpoint chính:</b>
 * <ul>
 *   <li>{@link #list(Boolean, String)}: {@code GET /recurring} - Danh sách giao dịch định kỳ</li>
 *   <li>{@link #detail(UUID)}: {@code GET /recurring/{id}} - Chi tiết giao dịch định kỳ</li>
 *   <li>{@link #create(CreateRecurringRequest)}: {@code POST /recurring} - Tạo mới giao dịch định kỳ</li>
 *   <li>{@link #update(UUID, UpdateRecurringRequest)}: {@code PATCH /recurring/{id}} - Cập nhật giao dịch định kỳ</li>
 *   <li>{@link #delete(UUID)}: {@code DELETE /recurring/{id}} - Xóa giao dịch định kỳ</li>
 *   <li>{@link #pause(UUID, PauseRecurringRequest)}: {@code POST /recurring/{id}/pause} - Tạm dừng/Bật lại</li>
 *   <li>{@link #runNow(UUID)}: {@code POST /recurring/{id}/run-now} - Thực hiện sinh giao dịch ngay</li>
 * </ul>
 * </p>
 *
 * @see RecurringService
 */
@RestController
@RequestMapping("/recurring")
@RequiredArgsConstructor
public class RecurringController {

    private final RecurringService recurringService;

    @GetMapping
    public ApiResponse<List<RecurringListItemResponse>> list(
            @RequestParam(name = "is_enabled", required = false) Boolean isEnabled,
            @RequestParam(name = "type", required = false) String type) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(recurringService.list(userId, isEnabled, type));
    }

    @GetMapping("/{id}")
    public ApiResponse<RecurringDetailResponse> detail(@PathVariable UUID id) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(recurringService.detail(userId, id));
    }

    @PostMapping
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RecurringListItemResponse> create(@Valid @RequestBody CreateRecurringRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(recurringService.create(userId, req));
    }

    @PatchMapping("/{id}")
    public ApiResponse<RecurringListItemResponse> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateRecurringRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(recurringService.update(userId, id, req));
    }

    /**
     * Xoá CỨNG bản ghi lịch — bảng không có cột {@code is_deleted}. Giao dịch đã sinh giữ nguyên
     * nhờ {@code fk_txn_recurring ON DELETE SET NULL}; xem {@code RecurringService.delete}.
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        UUID userId = SecurityContextUtil.currentUserId();
        recurringService.delete(userId, id);
        return ApiResponse.of(null);
    }

    @PostMapping("/{id}/pause")
    public ApiResponse<RecurringListItemResponse> pause(
            @PathVariable UUID id, @Valid @RequestBody PauseRecurringRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(recurringService.pause(userId, id, req));
    }

    // Ghi ngay không đợi tới hạn, KHÔNG làm đổi {@code next_run_date} (api/09 mục A3).
    @PostMapping("/{id}/run-now")
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RunNowResponse> runNow(@PathVariable UUID id) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(recurringService.runNow(userId, id));
    }
}

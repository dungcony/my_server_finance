package com.datn.financeapp.recurring.controller;

import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.recurring.dto.request.CreateRecurringRequest;
import com.datn.financeapp.recurring.dto.request.PauseRecurringRequest;
import com.datn.financeapp.recurring.dto.response.RecurringDetailResponse;
import com.datn.financeapp.recurring.dto.response.RecurringListItemResponse;
import com.datn.financeapp.recurring.dto.response.RunNowResponse;
import com.datn.financeapp.recurring.dto.request.UpdateRecurringRequest;
import com.datn.financeapp.recurring.service.RecurringService;
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
 * 7 điểm cuối giao dịch định kỳ (RECUR-01..03, api/09-DINH-KY-MUC-TIEU.md Phần A bảng "Danh sách
 * điểm cuối").
 *
 * <p>Hai POST gắn {@link Idempotent} theo D-11: {@code POST /recurring} vì gọi lặp do mạng chập
 * chờn sẽ tạo hai lịch song song cùng trừ tiền mỗi tháng, và {@code run-now} vì nó sinh giao dịch
 * thật trừ ví ngay. {@code pause} không cần — đặt cùng một giá trị nhiều lần vốn đã vô hại.
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

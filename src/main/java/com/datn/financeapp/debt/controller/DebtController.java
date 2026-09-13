package com.datn.financeapp.debt.controller;

import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.debt.dto.request.CreateDebtRequest;
import com.datn.financeapp.debt.dto.response.CreateDebtResponse;
import com.datn.financeapp.debt.dto.request.CreatePaymentRequest;
import com.datn.financeapp.debt.dto.response.CreatePaymentResponse;
import com.datn.financeapp.debt.dto.response.DebtDetailResponse;
import com.datn.financeapp.debt.dto.response.DebtListItemResponse;
import com.datn.financeapp.debt.dto.response.DebtSummaryResponse;
import com.datn.financeapp.debt.dto.request.UpdateDebtRequest;
import com.datn.financeapp.debt.dto.request.WriteOffRequest;
import com.datn.financeapp.debt.service.DebtService;
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
 * 9 endpoint sổ nợ (DEBT-01..06, api/08-SO-NO.md bảng "Danh sách điểm cuối").
 *
 * <p>Hai POST tạo mới gắn {@link Idempotent} theo D-11: tạo khoản nợ và ghi một lần trả — cả hai
 * đều sinh giao dịch thật, gọi lặp do mạng chập chờn sẽ trừ ví hai lần nếu không chống trùng.
 * {@code write-off} KHÔNG cần vì chỉ đổi trạng thái, gọi lại lần hai đã bị chặn bằng 409.
 *
 * <p><b>Thứ tự khai báo route quan trọng:</b> {@code /debts/summary} phải đứng TRƯỚC
 * {@code /debts/{id}} — nếu không Spring sẽ khớp "summary" vào {@code {id}} rồi ném lỗi ép kiểu
 * UUID (cùng bài học với BudgetController).
 */
@RestController
@RequestMapping("/debts")
@RequiredArgsConstructor
public class DebtController {

    private final DebtService debtService;

    @GetMapping
    public ApiResponse<List<DebtListItemResponse>> list(
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "status", required = false, defaultValue = "outstanding") String status,
            @RequestParam(name = "is_overdue", required = false) Boolean isOverdue) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(debtService.list(userId, type, status, isOverdue));
    }

    @GetMapping("/summary")
    public ApiResponse<DebtSummaryResponse> summary() {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(debtService.summary(userId));
    }

    @GetMapping("/{id}")
    public ApiResponse<DebtDetailResponse> detail(@PathVariable UUID id) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(debtService.detail(userId, id));
    }

    @PostMapping
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateDebtResponse> create(@Valid @RequestBody CreateDebtRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(debtService.create(userId, req));
    }

    @PatchMapping("/{id}")
    public ApiResponse<DebtListItemResponse> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateDebtRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(debtService.update(userId, id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        UUID userId = SecurityContextUtil.currentUserId();
        debtService.delete(userId, id);
        return ApiResponse.of(null);
    }

    @PostMapping("/{id}/payments")
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreatePaymentResponse> addPayment(
            @PathVariable UUID id, @Valid @RequestBody CreatePaymentRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(debtService.addPayment(userId, id, req));
    }

    @DeleteMapping("/{id}/payments/{paymentId}")
    public ApiResponse<Void> cancelPayment(@PathVariable UUID id, @PathVariable UUID paymentId) {
        UUID userId = SecurityContextUtil.currentUserId();
        debtService.cancelPayment(userId, id, paymentId);
        return ApiResponse.of(null);
    }

    @PostMapping("/{id}/write-off")
    public ApiResponse<DebtListItemResponse> writeOff(
            @PathVariable UUID id, @RequestBody(required = false) WriteOffRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(debtService.writeOff(userId, id, req));
    }
}

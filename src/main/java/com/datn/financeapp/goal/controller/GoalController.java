package com.datn.financeapp.goal.controller;

import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.goal.dto.CreateContributionRequest;
import com.datn.financeapp.goal.dto.CreateContributionResponse;
import com.datn.financeapp.goal.dto.CreateGoalRequest;
import com.datn.financeapp.goal.dto.CreateGoalResponse;
import com.datn.financeapp.goal.dto.GoalDetailResponse;
import com.datn.financeapp.goal.dto.GoalListItemResponse;
import com.datn.financeapp.goal.dto.UpdateGoalRequest;
import com.datn.financeapp.goal.service.GoalService;
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
 * 7 endpoint mục tiêu tiết kiệm (GOAL-01..04, api/09-DINH-KY-MUC-TIEU.md Phần B bảng "Danh sách
 * điểm cuối").
 *
 * <p>Hai POST tạo mới gắn {@link Idempotent} theo D-11 — riêng
 * {@code POST /goals/{id}/contributions} là bắt buộc vì ở chế độ {@code create_transaction = true}
 * nó sinh giao dịch chuyển tiền thật; gọi lặp do mạng chập chờn sẽ trừ ví hai lần nếu không chống
 * trùng. {@code POST /goals} cũng gắn vì {@code initial_amount} sinh bản ghi
 * {@code goal_contributions}, gọi lặp sẽ nhân đôi tiến độ ban đầu.
 */
@RestController
@RequestMapping("/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    @GetMapping
    public ApiResponse<List<GoalListItemResponse>> list(
            @RequestParam(name = "status", required = false) String status) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(goalService.list(userId, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<GoalDetailResponse> detail(@PathVariable UUID id) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(goalService.detail(userId, id));
    }

    @PostMapping
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateGoalResponse> create(@Valid @RequestBody CreateGoalRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(goalService.create(userId, req));
    }

    @PatchMapping("/{id}")
    public ApiResponse<GoalListItemResponse> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateGoalRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(goalService.update(userId, id, req));
    }

    /**
     * api/09 mục B5 — {@code revert_transactions} mặc định {@code false}: tiền đã chuyển vào ví
     * tiết kiệm thì vẫn nằm đó, chỉ là không theo dõi mục tiêu nữa.
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable UUID id,
            @RequestParam(name = "revert_transactions", required = false, defaultValue = "false")
                    boolean revertTransactions) {
        UUID userId = SecurityContextUtil.currentUserId();
        goalService.delete(userId, id, revertTransactions);
        return ApiResponse.of(null);
    }

    @PostMapping("/{id}/contributions")
    @Idempotent
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateContributionResponse> addContribution(
            @PathVariable UUID id, @Valid @RequestBody CreateContributionRequest req) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(goalService.addContribution(userId, id, req));
    }

    @DeleteMapping("/{id}/contributions/{contributionId}")
    public ApiResponse<Void> cancelContribution(@PathVariable UUID id, @PathVariable UUID contributionId) {
        UUID userId = SecurityContextUtil.currentUserId();
        goalService.cancelContribution(userId, id, contributionId);
        return ApiResponse.of(null);
    }
}

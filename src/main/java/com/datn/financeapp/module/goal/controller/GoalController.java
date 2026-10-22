package com.datn.financeapp.module.goal.controller;

import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.goal.dto.request.CreateContributionRequest;
import com.datn.financeapp.module.goal.dto.request.CreateGoalRequest;
import com.datn.financeapp.module.goal.dto.request.GoalFilterRequest;
import com.datn.financeapp.module.goal.dto.request.UpdateGoalRequest;
import com.datn.financeapp.module.goal.dto.response.CreateContributionResponse;
import com.datn.financeapp.module.goal.dto.response.CreateGoalResponse;
import com.datn.financeapp.module.goal.dto.response.GoalDetailResponse;
import com.datn.financeapp.module.goal.dto.response.GoalListItemResponse;
import com.datn.financeapp.module.goal.service.GoalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller tiếp nhận các yêu cầu HTTP quản lý mục tiêu tiết kiệm.
 * <p>
 * <b>Các endpoint chính:</b>
 * <ul>
 *   <li>{@link #list(String)}: {@code GET /goals} - Danh sách mục tiêu tiết kiệm của người dùng</li>
 *   <li>{@link #detail(UUID)}: {@code GET /goals/{id}} - Chi tiết mục tiêu tiết kiệm</li>
 *   <li>{@link #create(CreateGoalRequest)}: {@code POST /goals} - Tạo mới mục tiêu tiết kiệm</li>
 *   <li>{@link #update(UUID, UpdateGoalRequest)}: {@code PATCH /goals/{id}} - Cập nhật mục tiêu tiết kiệm</li>
 *   <li>{@link #delete(UUID, boolean, boolean)}: {@code DELETE /goals/{id}} - Xóa mục tiêu tiết kiệm</li>
 *   <li>{@link #addContribution(UUID, CreateContributionRequest)}: {@code POST /goals/{id}/contributions} - Đóng góp/nạp tiền</li>
 *   <li>{@link #cancelContribution(UUID, UUID)}: {@code DELETE /goals/{id}/contributions/{contributionId}} - Hủy đợt đóng góp</li>
 *   <li>{@link #adminList(GoalFilterRequest, Pageable)}: {@code GET /goals/admin/all} - Danh sách mục tiêu toàn hệ thống (Admin)</li>
 *   <li>{@link #adminDetail(UUID)}: {@code GET /goals/admin/detail/{id}} - Chi tiết mục tiêu bất kỳ (Admin)</li>
 *   <li>{@link #adminDelete(UUID, boolean, boolean)}: {@code DELETE /goals/admin/{id}} - Xóa mục tiêu (Admin)</li>
 * </ul>
 * </p>
 *
 * @see GoalService
 */
@RestController
@RequestMapping("/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    private static final String MANAGE_GOAL_READ =
            "hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).GOAL_READ.name())";
    private static final String MANAGE_GOAL_DELETE =
            "hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).GOAL_DELETE.name())";

    // ---------------------------------------------------------------------
    // Phân hệ Người dùng (User)
    // ---------------------------------------------------------------------

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

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable UUID id,
            @RequestParam(name = "revert_transactions", required = false, defaultValue = "false")
            boolean revertTransactions,
            @RequestParam(name = "delete_wallet", required = false, defaultValue = "false")
            boolean deleteWallet) {
        UUID userId = SecurityContextUtil.currentUserId();
        goalService.delete(userId, id, revertTransactions, deleteWallet);
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

    // ---------------------------------------------------------------------
    // Phân hệ Quản trị (Admin)
    // ---------------------------------------------------------------------

    @PreAuthorize(MANAGE_GOAL_READ)
    @GetMapping("/admin/all")
    public ApiResponse<Page<GoalListItemResponse>> adminList(
            @ModelAttribute GoalFilterRequest filter,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.of(goalService.adminList(filter, pageable));
    }

    @PreAuthorize(MANAGE_GOAL_READ)
    @GetMapping("/admin/detail/{id}")
    public ApiResponse<GoalDetailResponse> adminDetail(@PathVariable UUID id) {
        return ApiResponse.of(goalService.adminDetail(id));
    }

    @PreAuthorize(MANAGE_GOAL_DELETE)
    @DeleteMapping("/admin/{id}")
    public ApiResponse<Void> adminDelete(
            @PathVariable UUID id,
            @RequestParam(name = "revert_transactions", required = false, defaultValue = "false")
            boolean revertTransactions,
            @RequestParam(name = "delete_wallet", required = false, defaultValue = "false")
            boolean deleteWallet) {
        goalService.adminDelete(id, revertTransactions, deleteWallet);
        return ApiResponse.of(null);
    }
}

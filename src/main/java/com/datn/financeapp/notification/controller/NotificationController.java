package com.datn.financeapp.notification.controller;

import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.response.PageRequestParams;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.notification.dto.response.NotificationListResponse;
import com.datn.financeapp.notification.service.NotificationService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hai điểm cuối đọc/đánh dấu đã đọc thông báo (api/11-THONG-BAO.md, D-39). Hạ tầng đi đầu Phase 4
 * — bảng {@code notifications} là nền cho JOB-04 (nhắc nợ) và BUDGET-06/D-41 (cảnh báo ngân
 * sách) ở các plan sau.
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public NotificationListResponse list(
            @RequestParam(name = "is_read", required = false) Boolean isRead,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize) {
        UUID userId = SecurityContextUtil.currentUserId();
        PageRequestParams pageParams = PageRequestParams.of(page, pageSize, "created_at", "desc");
        return notificationService.list(userId, isRead, pageParams);
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable UUID id) {
        UUID userId = SecurityContextUtil.currentUserId();
        notificationService.markRead(userId, id);
        return ApiResponse.of(null);
    }
}

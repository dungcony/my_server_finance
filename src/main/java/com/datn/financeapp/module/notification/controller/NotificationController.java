package com.datn.financeapp.module.notification.controller;

import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.notification.dto.response.NotificationListResponse;
import com.datn.financeapp.module.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;


@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<NotificationListResponse> list(
            @RequestParam(name = "is_read", required = false) Boolean isRead,
            Pageable pageable) {
        UUID userId = SecurityContextUtil.currentUserId();
        return ApiResponse.of(notificationService.list(userId, isRead, pageable));
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable UUID id) {
        UUID userId = SecurityContextUtil.currentUserId();
        notificationService.markRead(userId, id);
        return ApiResponse.of(null);
    }
}

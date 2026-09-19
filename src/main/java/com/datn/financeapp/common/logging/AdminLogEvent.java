package com.datn.financeapp.common.logging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminLogEvent {
    private long id;
    private String timestamp;
    private String level;
    private String logger;
    private String thread;
    private String message;
    /** Lấy từ MDC "requestId" (RequestLoggingFilter) — rỗng nếu log phát sinh ngoài vòng đời request. */
    private String requestId;
    /** Lấy từ MDC "userId" (JwtAuthFilter, chỉ có sau khi xác thực) — rỗng nếu chưa đăng nhập. */
    private String userId;
    /** Nhãn nguồn ngắn gọn để lọc: HTTP / Security / Auth / Database / App. */
    private String source;
    /** Stack trace đầy đủ nếu log event có kèm exception, ngược lại null. */
    private String stackTrace;
    /** API endpoint nếu là HTTP request hoặc log trong vòng đời request, ví dụ: "GET /v1/auth/login" */
    private String api;
    /** HTTP Status code nếu có (200, 401, 500...) */
    private Integer status;
    /** Thời gian thực thi tính bằng ms nếu có (độ trễ xử lý request) */
    private Long durationMs;
}

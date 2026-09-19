package com.datn.financeapp.common.logging;

import java.util.regex.Pattern;

/**
 * Che dữ liệu nhạy cảm trước khi log được stream ra trang admin console
 * ({@link AdminLogStreamService}). Trang này chỉ yêu cầu ROLE_ADMIN, nhưng theo nguyên
 * tắc "riêng tư mặc định" của dự án, log không được để lộ nguyên văn email, mật khẩu,
 * OTP hay bearer token dù người xem là admin.
 *
 * <p>Chỉ che theo tên field JSON đã biết trước (password/otp/token/...) thay vì đoán mọi
 * chuỗi số — tránh che nhầm dữ liệu nghiệp vụ bình thường như {@code amount}.
 */
final class LogMasker {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("\\b([a-zA-Z0-9])[a-zA-Z0-9._%+-]*(@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})\\b");

    // "password":"...", "otp": "...", "token":"...", ... -> giữ key, che value.
    private static final Pattern SENSITIVE_JSON_FIELD_PATTERN = Pattern.compile(
            "(?i)(\"(?:password|new_password|newPassword|old_password|oldPassword"
                    + "|confirm_password|confirmPassword|otp|access_token|refresh_token|token)\"\\s*:\\s*\")"
                    + "[^\"]*(\")");

    private static final Pattern BEARER_TOKEN_PATTERN =
            Pattern.compile("Bearer\\s+[A-Za-z0-9\\-_.]+");

    private LogMasker() {
    }

    static String mask(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        String result = BEARER_TOKEN_PATTERN.matcher(message).replaceAll("Bearer [MASKED_TOKEN]");
        result = SENSITIVE_JSON_FIELD_PATTERN.matcher(result).replaceAll("$1[MASKED]$2");
        result = EMAIL_PATTERN.matcher(result).replaceAll("$1***$2");
        return result;
    }
}

package com.datn.financeapp.auth.dto;

/**
 * Body của POST /auth/logout (api/01-XAC-THUC.md mục 4).
 *
 * LƯU Ý: api/01 mục 4 chỉ đặc tả rõ {@code logout_all_devices} (mặc định false), không nêu rõ
 * trường mang refresh token. Vì logout PHẢI biết token nào để revoke, suy luận hợp lý duy nhất
 * là body cũng nhận {@code refresh_token} giống cấu trúc /auth/refresh — quyết định điền vào
 * chỗ trống hợp lý, ghi rõ trong SUMMARY.md, không phải tự bịa field ngoài ý đồ tài liệu.
 */
public record LogoutRequest(String refreshToken, boolean logoutAllDevices) {
}

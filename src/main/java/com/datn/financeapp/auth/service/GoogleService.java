package com.datn.financeapp.auth.service;

/**
 * D3: kiểm chứng chữ ký {@code id_token} của Google.
 *
 * <p>Tách interface khỏi implementation vì cùng lý do với {@link PasswordResetNotifier}: test
 * không được gọi ra mạng ngoài. Bản thật tải khoá công khai từ máy chủ Google, nên trong test
 * nó sẽ chậm, phụ thuộc mạng, và không có cách nào sinh ra token hợp lệ để thử.
 */
public interface GoogleService {

    /**
     * @param idToken chuỗi JWT client gửi lên
     * @return thông tin đã kiểm chứng
     * @throws com.datn.financeapp.common.exception.BusinessException mã
     *                                                                {@code INVALID_GOOGLE_TOKEN} nếu chữ ký sai, hết hạn, {@code aud} không khớp,
     *                                                                hoặc {@code iss} lạ
     */
    GoogleUserInfo verifyIdToken(String idToken);

    default GoogleUserInfo verify(String idToken) {
        return verifyIdToken(idToken);
    }

    /**
     * Phần dữ liệu duy nhất luồng đăng nhập cần lấy ra từ token.
     *
     * @param googleId trường {@code sub} — định danh KHÔNG đổi kể cả khi người dùng đổi email
     *                 bên Google, nên đây mới là thứ đáng lưu vào {@code users.google_id}
     * @param email    hộp thư Google đã xác thực
     * @param name     tên hiển thị, có thể rỗng — chỉ dùng lúc TẠO tài khoản, không đồng bộ lại
     *                 ở những lần đăng nhập sau (prd/01 mục 9.4b)
     */
    record GoogleUserInfo(String googleId, String email, String name) {
    }
}

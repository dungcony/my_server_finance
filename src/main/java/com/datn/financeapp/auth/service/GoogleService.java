package com.datn.financeapp.auth.service;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.auth.helper.GoogleUserInfo;

/**
 * D3: kiểm chứng chữ ký {@code id_token} của Google.
 * không được gọi ra mạng ngoài. Bản thật tải khoá công khai từ máy chủ Google, nên trong test
 * nó sẽ chậm, phụ thuộc mạng, và không có cách nào sinh ra token hợp lệ để thử.
 */
public interface GoogleService {

    /**
     * @param idToken chuỗi JWT client gửi lên
     * @return thông tin đã kiểm chứng
     * @throws BusinessException mã{@code AUTH_GOOGLE_TOKEN_INVALID} nếu chữ ký sai, hết hạn
     */
    GoogleUserInfo verifyIdToken(String idToken);

}

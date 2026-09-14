package com.datn.financeapp.auth.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.datn.financeapp.auth.service.GoogleService;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

/**
 * D3: bản kiểm chứng thật, gọi ra máy chủ Google để lấy khoá công khai.
 *
 * <p>
 * Thư viện {@code google-api-client} lo bốn việc: khớp chữ ký với khoá công
 * khai của Google,
 * kiểm {@code iss} thuộc hai giá trị hợp lệ, kiểm {@code aud} bằng đúng web
 * client id của dự án,
 * và kiểm token chưa hết hạn. Khoá công khai được cache và tự xoay khi Google
 * đổi.
 *
 * <p>
 * Tự viết phần này bằng tay là chỗ rất dễ sai — mà sai thì im lặng: một lỗi bỏ
 * sót bước kiểm
 * {@code aud} vẫn cho đăng nhập thành công trong mọi lần thử tay, chỉ có kẻ tấn
 * công mới phát
 * hiện ra.
 */
@Component
@Slf4j
public class GoogleServiceImpl implements GoogleService {

    private final String webClientId;
    private GoogleIdTokenVerifier delegate;

    public GoogleServiceImpl(@Value("${google.web-client-id:}") String webClientId) {
        this.webClientId = webClientId;
    }

    @PostConstruct
    void init() {
        if (webClientId.isBlank()) {
            // Không ném lỗi để backend vẫn khởi động được khi chưa cấu hình Google — mọi
            // endpoint khác không liên quan. Chỉ /auth/google trả lỗi khi bị gọi.
            log.warn("GOOGLE_WEB_CLIENT_ID chưa cấu hình — POST /auth/google sẽ luôn từ chối.");
            return;
        }
        delegate = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(webClientId))
                .build();
    }

    @Override
    public GoogleUserInfo verifyIdToken(String idToken) {
        if (delegate == null) {
            throw new BusinessException(ErrorCode.INVALID_GOOGLE_TOKEN,
                    "Đăng nhập bằng Google chưa được cấu hình trên máy chủ.");
        }

        GoogleIdToken token;
        try {
            token = delegate.verify(idToken);
        } catch (GeneralSecurityException | IOException | IllegalArgumentException e) {
            // IOException gộp chung ở đây là có chủ đích thu hẹp: nó nghĩa là không gọi
            // được
            // máy chủ khoá của Google, tức lỗi phía ta chứ không phải token hỏng. Nhưng
            // phân
            // biệt ra cũng không giúp gì cho client — họ chỉ có thể thử lại. Chi tiết vào
            // log.
            log.warn("Kiểm chứng id_token Google thất bại: {}", e.getMessage());
            throw invalidToken();
        }

        // verify() trả null khi chữ ký sai, aud không khớp, iss lạ, hoặc token hết hạn
        // — bốn
        // nguyên nhân cố ý gộp làm một mã lỗi (api/01 mục 13): phân biệt ra chỉ giúp kẻ
        // đang
        // thử giả token biết mình sai ở đâu.
        if (token == null) {
            log.warn("id_token Google không hợp lệ (chữ ký, aud, iss, hoặc đã hết hạn).");
            throw invalidToken();
        }

        GoogleIdToken.Payload payload = token.getPayload();

        // Google chỉ đảm bảo email đã xác thực khi email_verified = true. Không kiểm
        // thì một
        // tài khoản Google Workspace cấu hình lạ có thể mang email của người khác, và
        // bước
        // "email trùng thì tự liên kết" sẽ trao tài khoản nạn nhân cho kẻ tấn công.
        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            log.warn("id_token Google có email chưa xác thực: {}", payload.getEmail());
            throw invalidToken();
        }

        return new GoogleUserInfo(
                payload.getSubject(),
                payload.getEmail(),
                (String) payload.get("name"));
    }

    private BusinessException invalidToken() {
        return new BusinessException(ErrorCode.INVALID_GOOGLE_TOKEN);
    }
}

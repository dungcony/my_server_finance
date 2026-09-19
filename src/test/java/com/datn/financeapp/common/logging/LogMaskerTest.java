package com.datn.financeapp.common.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm tra {@link LogMasker} che các loại dữ liệu nhạy cảm trước khi log được stream
 * ra trang admin console — trang này chỉ yêu cầu ROLE_ADMIN nhưng vẫn phải theo nguyên
 * tắc "riêng tư mặc định" của dự án, không để lộ email đầy đủ/mật khẩu/OTP/token qua log.
 */
class LogMaskerTest {

    @Test
    @DisplayName("Che phần local của email, giữ lại ký tự đầu và tên miền")
    void masksEmailKeepingFirstCharAndDomain() {
        String input = "User đăng nhập: dominhduc25013615@gmail.com thành công";

        String result = LogMasker.mask(input);

        assertThat(result).doesNotContain("dominhduc25013615@gmail.com");
        assertThat(result).contains("d***@gmail.com");
    }

    @Test
    @DisplayName("Che giá trị field password trong message dạng JSON")
    void masksPasswordFieldInJson() {
        String input = "Request body: {\"email\":\"a@b.com\",\"password\":\"Sup3rSecret!\"}";

        String result = LogMasker.mask(input);

        assertThat(result).doesNotContain("Sup3rSecret!");
        assertThat(result).contains("\"password\":\"[MASKED]\"");
    }

    @Test
    @DisplayName("Che giá trị field otp trong message dạng JSON")
    void masksOtpFieldInJson() {
        String input = "Verify OTP request: {\"email\":\"a@b.com\",\"otp\":\"123456\"}";

        String result = LogMasker.mask(input);

        assertThat(result).doesNotContain("123456");
        assertThat(result).contains("\"otp\":\"[MASKED]\"");
    }

    @Test
    @DisplayName("Che token trong header Authorization: Bearer <token>")
    void masksBearerToken() {
        String input = "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.abc123signature";

        String result = LogMasker.mask(input);

        assertThat(result).doesNotContain("eyJhbGciOiJIUzI1NiJ9");
        assertThat(result).contains("Bearer [MASKED_TOKEN]");
    }

    @Test
    @DisplayName("Không đổi message không chứa dữ liệu nhạy cảm")
    void leavesNonSensitiveMessageUnchanged() {
        String input = "--> GET /v1/wallets | status=200 | time=12ms | user=anonymous | reqId=abc-123";

        String result = LogMasker.mask(input);

        assertThat(result).isEqualTo(input);
    }

    @Test
    @DisplayName("Không che nhầm các field tiền tệ thông thường như amount")
    void doesNotMaskOrdinaryAmountField() {
        String input = "Tạo giao dịch: {\"amount\":500000,\"type\":\"expense\"}";

        String result = LogMasker.mask(input);

        assertThat(result).isEqualTo(input);
    }

    @Test
    @DisplayName("Trả về null/rỗng an toàn khi message null hoặc rỗng")
    void handlesNullAndEmptyMessageSafely() {
        assertThat(LogMasker.mask(null)).isNull();
        assertThat(LogMasker.mask("")).isEmpty();
    }
}

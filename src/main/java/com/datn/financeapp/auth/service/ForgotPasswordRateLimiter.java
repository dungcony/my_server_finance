package com.datn.financeapp.auth.service;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * C4 — tầng giới hạn thứ hai cho {@code POST /auth/forgot-password}: đếm theo EMAIL trong nội
 * dung yêu cầu, 5 lần mỗi giờ (api/01-XAC-THUC.md mục 11).
 *
 * <p><b>Vì sao không nhét vào {@code RateLimitFilter}.</b> Filter chỉ nhìn thấy URI và IP; đọc
 * email thì phải đọc thân request, mà thân request chỉ đọc được một lần — filter đọc xong thì
 * Controller nhận rỗng. Vì thế tầng này chạy ở tầng service, nơi DTO đã được parse sẵn.
 *
 * <p><b>Độc lập hoàn toàn với tầng IP</b> (5 lần/phút cho mọi {@code /v1/auth/}): hai bộ đếm
 * riêng, chạm cái nào trước thì chặn cái đó, cùng trả {@code 429 RATE_LIMIT_EXCEEDED}. Tầng IP
 * một mình không chặn được kịch bản dội bom hộp thư — kẻ xấu gọi 5 lần/phút rồi nghỉ, sau một
 * giờ đã đẩy hàng trăm mail vào hộp thư nạn nhân mà vẫn dưới hạn mức.
 *
 * <p>Bộ đếm nằm trong bộ nhớ, khởi động lại máy chủ thì về 0 — chấp nhận được, vì đây không
 * phải rào chắn duy nhất: mã đặt lại sống 15 phút và chỉ dùng được một lần.
 */
@Component
public class ForgotPasswordRateLimiter {

    private final int maxRequestsPerEmail;
    private final Duration window;

    /**
     * {@code expireAfterAccess} phải DÀI HƠN cửa sổ đếm: hết hạn sớm là xoá bucket còn dở lượt,
     * lần gọi kế tiếp dựng bucket mới đầy 5 lượt và hạn mức thành vô nghĩa.
     */
    private final Cache<String, Bucket> buckets;

    public ForgotPasswordRateLimiter(
            @Value("${rate-limit.max-request-email-forgot-password:5}") int maxRequestsPerEmail,
            @Value("${rate-limit.reset-request-email-forgot-password:1h}") Duration window) {
        this.maxRequestsPerEmail = maxRequestsPerEmail;
        this.window = window;
        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(window.plusMinutes(5))
                .maximumSize(100_000)
                .build();
    }

    /**
     * Tiêu một lượt của {@code email}, ném {@code 429} khi hết lượt.
     *
     * <p>Gọi TRƯỚC khi tra email có tồn tại hay không: nếu chỉ đếm khi thật sự gửi mail thì
     * email chưa đăng ký không tốn lượt, và chênh lệch đó lại thành một cách dò danh sách người
     * dùng — đúng thứ mà quy tắc "luôn trả 200" của điểm cuối này muốn tránh.
     */
    public void consume(String email) {
        Bucket bucket = buckets.get(
                email.toLowerCase(),
                k -> Bucket.builder()
                        .addLimit(l -> l.capacity(maxRequestsPerEmail)
                                .refillGreedy(maxRequestsPerEmail, window))
                        .build());

        if (!bucket.tryConsume(1)) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED, "Bạn đã yêu cầu mã quá nhiều lần, vui lòng thử lại sau một giờ.");
        }
    }
}

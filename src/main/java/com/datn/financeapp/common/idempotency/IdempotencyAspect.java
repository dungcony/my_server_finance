package com.datn.financeapp.common.idempotency;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * AOP chặn method controller đánh dấu {@link Idempotent} (CORE-03, D-10 đến D-13).
 *
 * Luồng:
 * 1. Không có header {@code Idempotency-Key} -> chạy nghiệp vụ bình thường, không đụng DB.
 * 2. {@code INSERT ... ON CONFLICT DO NOTHING} với status=processing:
 *    - Insert thành công (chưa từng có key này) -> chạy nghiệp vụ thật, lưu kết quả, đổi
 *      status=completed.
 *    - Insert thất bại (đã tồn tại) -> tra bản ghi cũ:
 *        - completed -> trả lại response_body đã lưu, KHÔNG chạy lại nghiệp vụ.
 *        - processing -> ném 409 REQUEST_IN_PROGRESS ngay lập tức (D-13/D-14).
 * 3. Nghiệp vụ ném exception -> xoá bản ghi processing (không cache lỗi), throw lại nguyên
 *    exception gốc để GlobalExceptionHandler xử lý tiếp bình thường.
 *
 * Dùng {@link RequestContextHolder} thay vì inject {@link HttpServletRequest} trực tiếp vào
 * field của bean singleton {@code @Aspect @Component} — an toàn hơn trong {@code @Around}.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyAspect {

    private static final String HEADER_NAME = "Idempotency-Key";
    private static final String STATUS_COMPLETED = "completed";

    private final IdempotencyKeyRepository repository;
    private final ObjectMapper objectMapper;
    private final IdempotencyTransactionHelper transactionHelper;

    @Around("@annotation(com.datn.financeapp.common.idempotency.Idempotent)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        HttpServletRequest request = currentRequest();
        String key = request.getHeader(HEADER_NAME);

        if (key == null || key.isBlank()) {
            return pjp.proceed();
        }

        UUID userId = SecurityContextUtil.currentUserId();
        String endpoint = request.getMethod() + " " + request.getRequestURI();

        return handleIdempotent(pjp, key, userId, endpoint);
    }

    private Object handleIdempotent(ProceedingJoinPoint pjp, String key, UUID userId, String endpoint)
            throws Throwable {
        int inserted = transactionHelper.tryInsertProcessing(key, userId, endpoint);

        if (inserted == 0) {
            IdempotencyKeyEntity existing = repository
                    .findByIdempotencyKeyAndUserIdAndEndpoint(key, userId, endpoint)
                    .orElseThrow(() -> new BusinessException(
                            "INTERNAL_ERROR", 500, "Không đọc lại được bản ghi idempotency vừa xung đột."));

            if (STATUS_COMPLETED.equals(existing.getStatus())) {
                return buildResponseFromCache(existing);
            }

            throw new BusinessException(
                    "REQUEST_IN_PROGRESS", 409, "Yêu cầu trước đó đang được xử lý, vui lòng thử lại sau.");
        }

        try {
            Object result = pjp.proceed();
            markCompleted(key, userId, endpoint, result);
            return result;
        } catch (Exception ex) {
            transactionHelper.deleteRecord(key, userId, endpoint);
            throw ex;
        }
    }

    private void markCompleted(String key, UUID userId, String endpoint, Object result) {
        IdempotencyKeyEntity existing = repository
                .findByIdempotencyKeyAndUserIdAndEndpoint(key, userId, endpoint)
                .orElseThrow(() -> new BusinessException(
                        "INTERNAL_ERROR", 500, "Không tìm thấy bản ghi idempotency để cập nhật completed."));

        int status = HttpStatus.OK.value();
        Object body = result;
        if (result instanceof ResponseEntity<?> responseEntity) {
            status = responseEntity.getStatusCode().value();
            body = responseEntity.getBody();
        }

        try {
            existing.setStatus(STATUS_COMPLETED);
            existing.setResponseStatus(status);
            existing.setResponseBody(objectMapper.writeValueAsString(body));
            transactionHelper.save(existing);
        } catch (Exception e) {
            log.error("Lỗi khi serialize response để lưu idempotency cache, xoá bản ghi processing", e);
            transactionHelper.deleteRecord(key, userId, endpoint);
        }
    }

    @SuppressWarnings("unchecked")
    private Object buildResponseFromCache(IdempotencyKeyEntity existing) throws Exception {
        Object parsed = existing.getResponseBody() == null
                ? null
                : objectMapper.readValue(existing.getResponseBody(), Object.class);
        int status = existing.getResponseStatus() != null ? existing.getResponseStatus() : HttpStatus.OK.value();
        return ResponseEntity.status(status).body(parsed);
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        return attributes.getRequest();
    }
}

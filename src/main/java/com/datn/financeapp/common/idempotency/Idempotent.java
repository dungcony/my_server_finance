package com.datn.financeapp.common.idempotency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Đánh dấu method controller là POST-tạo-mới cần bảo vệ chống ghi trùng khi client
 * gửi lại request với cùng header {@code Idempotency-Key} (CORE-03, D-10).
 *
 * KHÔNG gắn lên POST-hành-động ({@code /auth/login}, {@code /auth/refresh},
 * {@code /auth/logout} — D-11): gắn nhầm khiến lần đăng nhập thứ hai nhận lại
 * token cũ đã cache, nguy hiểm hơn hẳn rủi ro quên gắn.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {}

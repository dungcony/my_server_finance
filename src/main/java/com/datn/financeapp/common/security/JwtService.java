package com.datn.financeapp.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.MacAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * Sinh/verify JWT access token bằng API jjwt 0.13.x (Jwts.parser()/.verifyWith(key) —
 * KHÔNG dùng parserBuilder()/setSigningKey() đã lỗi thời của 0.11.x).
 *
 * AUTH-08: access token chỉ chứa sub (user id), claim plan, authorities, roles_level_top
 * và exp — không có trường nhạy cảm khác (password, email...). Mọi claim mới phải thêm qua
 * tham số riêng của generateAccessToken (không nhét tuỳ tiện vào Map) để buộc thay đổi sau
 * này phải sửa method signature, dễ review (T-02-04).
 *
 * roles_level_top = level của role MẠNH NHẤT user đang giữ (quy ước số nhỏ = quyền cao,
 * nên "mạnh nhất" = số NHỎ nhất — xem NO_ROLE_LEVEL bên dưới cho trường hợp không có role).
 * Chỉ dùng cho việc ĐỌC/LỌC (vd quản lý chỉ thấy user cấp thấp hơn) — KHÔNG dùng để chặn
 * hành động GHI (vd gán role) vì giá trị này "đông cứng" tới khi token hết hạn, có thể lệch
 * nếu role của user bị đổi giữa phiên. Hành động ghi nhạy cảm phải query lại level mới nhất
 * từ DB (xem AdminUserServiceImpl.addRoleToUser).
 */
@Service
public class JwtService {

    private static final MacAlgorithm ALG = Jwts.SIG.HS256;

    // Quy ước "số nhỏ = quyền cao" — user không có role nào phải là YẾU NHẤT, tức số cực lớn.
    private static final int NO_ROLE_LEVEL = Integer.MAX_VALUE;

    private final SecretKey key;
    private final long accessTokenExpirySeconds;

    public JwtService(
            @Value("${jwt.secret}") String base64Secret,
            @Value("${jwt.access-token-expiry-seconds:3600}") long accessTokenExpirySeconds) {
        // D-01: JWT_SECRET bắt buộc qua application.yml -> ${JWT_SECRET}, không default ở đây
        // cho giá trị secret. Nếu secret không đủ độ dài base64 cho HS256 (>=32 byte sau decode),
        // Keys.hmacShaKeyFor ném WeakKeyException ngay lúc khởi động bean — hành vi ĐÚNG
        // (fail-fast, T-02-03), không try/catch nuốt lỗi.
        this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(base64Secret));
        this.accessTokenExpirySeconds = accessTokenExpirySeconds;
    }

    public String generateAccessToken(UUID userId, String plan) {
        return generateAccessToken(userId, plan, java.util.Collections.emptyList(), NO_ROLE_LEVEL);
    }

    public String generateAccessToken(UUID userId, String plan, java.util.Collection<String> authorities) {
        return generateAccessToken(userId, plan, authorities, NO_ROLE_LEVEL);
    }

    public String generateAccessToken(UUID userId, String plan, java.util.Collection<String> authorities, int topRoleLevel) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(userId.toString())
                .claim("plan", plan);
        if (authorities != null && !authorities.isEmpty()) {
            builder.claim("authorities", authorities);
        }
        builder.claim("roles_level_top", topRoleLevel);
        return builder
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTokenExpirySeconds)))
                .signWith(key, ALG)
                .compact();
    }

    /**
     * Ném ExpiredJwtException / SignatureException / MalformedJwtException nếu token
     * hết hạn / sai chữ ký / sai định dạng. verifyWith(key) mặc định từ chối alg=none
     * và ép đúng thuật toán đã ký (T-02-01 — JWT algorithm confusion).
     */
    /** AUTH-08: dùng để trả đúng {@code expires_in} trong response register/login/refresh — tránh
     * hằng số trùng lặp lệch khỏi cấu hình thật {@code jwt.access-token-expiry-seconds}. */
    public long getAccessTokenExpirySeconds() {
        return accessTokenExpirySeconds;
    }

    public Claims parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

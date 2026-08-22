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
 * AUTH-08: access token chỉ chứa sub (user id), claim plan, và exp — không có trường
 * nhạy cảm khác. generateAccessToken chỉ nhận đúng 2 tham số (userId, plan) để buộc mọi
 * thay đổi sau này phải sửa method signature, dễ review (T-02-04).
 */
@Service
public class JwtService {

    private static final MacAlgorithm ALG = Jwts.SIG.HS256;

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
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("plan", plan)
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
    public Claims parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

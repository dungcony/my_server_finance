package com.datn.financeapp.common.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test thuần cho JwtService — không cần Spring context đầy đủ.
 * T-02-01/T-02-03: verify jjwt 0.13.x API mới, chống algorithm confusion, secret yếu fail-fast.
 */
class JwtServiceTest {

    private static final String TEST_SECRET =
            Base64.getEncoder().encodeToString("a-very-long-test-secret-key-32-bytes-min!".getBytes());

    private final JwtService jwtService = new JwtService(TEST_SECRET, 3600);

    @Test
    void generateAndParse_returnsCorrectSubjectAndPlanClaim() {
        UUID userId = UUID.randomUUID();

        String token = jwtService.generateAccessToken(userId, "free");
        var claims = jwtService.parseAndValidate(token);

        assertEquals(userId.toString(), claims.getSubject());
        assertEquals("free", claims.get("plan", String.class));
    }

    @Test
    void expiredToken_throwsExpiredJwtException() {
        JwtService expiredJwtService = new JwtService(TEST_SECRET, -10);
        String token = expiredJwtService.generateAccessToken(UUID.randomUUID(), "free");

        assertThrows(ExpiredJwtException.class, () -> jwtService.parseAndValidate(token));
    }

    @Test
    void tamperedSignature_throwsSignatureException() {
        String token = jwtService.generateAccessToken(UUID.randomUUID(), "free");
        String tampered = flipMiddleSignatureByte(token);

        assertThrows(SignatureException.class, () -> jwtService.parseAndValidate(tampered));
    }

    /**
     * Sửa chữ ký JWT theo cách KHÔNG THỂ là no-op — giải mã base64url phần chữ ký (segment thứ 3
     * sau dấu chấm cuối) thành mảng byte thật, đảo bit của byte ở GIỮA mảng rồi mã hoá lại. Cách
     * cũ (đổi ký tự cuối cùng của chuỗi base64url) có xác suất nhỏ rơi đúng vào phần đệm
     * (padding) không ảnh hưởng byte giải mã, khiến verify vẫn qua và test flaky khi chạy chung
     * full suite (không tái hiện khi chạy riêng) — xem deferred-items.md Phase 01.
     */
    private String flipMiddleSignatureByte(String token) {
        int lastDot = token.lastIndexOf('.');
        String headerAndPayload = token.substring(0, lastDot);
        String signatureSegment = token.substring(lastDot + 1);

        byte[] signatureBytes = Base64.getUrlDecoder().decode(signatureSegment);
        int middleIndex = signatureBytes.length / 2;
        signatureBytes[middleIndex] ^= 0xFF;

        String tamperedSignatureSegment =
                Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);
        return headerAndPayload + "." + tamperedSignatureSegment;
    }

    @Test
    void rejectsDifferentAlgorithm_HS384WithDifferentKey() {
        // Sinh token bằng key/thuật toán khác (HS384, key hợp lệ riêng) — parser cấu hình
        // đúng key HS256 phải từ chối vì chữ ký không khớp key đã cấu hình.
        var otherKey = io.jsonwebtoken.Jwts.SIG.HS384.key().build();

        String tokenSignedByOtherKey = io.jsonwebtoken.Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("plan", "free")
                .signWith(otherKey, io.jsonwebtoken.Jwts.SIG.HS384)
                .compact();

        // Với key HS256 đã cấu hình, jjwt từ chối token ký HS384 bằng key khác — có thể
        // là SignatureException (chữ ký sai) hoặc WeakKeyException (key không đủ mạnh cho
        // HS384 khi verify theo alg header) — cả hai đều là JwtException, đều là "từ chối
        // đúng", không có trường hợp nào chấp nhận nhầm token giả mạo thuật toán khác.
        assertThrows(JwtException.class, () -> jwtService.parseAndValidate(tokenSignedByOtherKey));
    }
}

package com.datn.financeapp.auth.service.impl;

import com.datn.financeapp.auth.dto.request.TokenCreateReq;
import com.datn.financeapp.auth.dto.response.TokenRes;
import com.datn.financeapp.auth.entity.RefreshToken;
import com.datn.financeapp.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.auth.service.TokenService;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class TokenServiceImpl implements TokenService {

    private final JwtService jwt;
    private final RefreshTokenRepository refreshTokenRepository;

    private static final SecureRandom secureRandom = new SecureRandom();
    private static final long refreshTokenTTL = 30;


    @Override
    public TokenRes create(TokenCreateReq req) {
        String refreshToken = saveAndGetRefresh(req.userId());
        String accessToken = jwt.generateAccessToken(req.userId(), req.plan(), req.authorities(), req.roleLevel());
        return new TokenRes(accessToken, refreshToken, jwt.getAccessTokenExpirySeconds());
    }


    @Transactional(noRollbackFor = BusinessException.class)
    @Override
    public UUID checkRefreshAndGetUserId(String refreshToken) {

        String hash = sha256Hex(refreshToken);

        // tìm refresh còn hoạt động
        RefreshToken activeToken = refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(hash)
                .orElse(null);

        if (activeToken != null) {
            if (activeToken.getExpiresAt().isBefore(Instant.now()))
                throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);

            activeToken.setRevokedAt(Instant.now());
            activeToken = refreshTokenRepository.save(activeToken);

            return activeToken.getUserId();
        }

        refreshTokenRepository.findByTokenHash(hash)
                .ifPresent(revoked -> refreshTokenRepository.revokeAllActiveForUser(revoked.getUserId()));

        throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
    }

    @Override
    public RefreshToken revokeRefresh(String refresh) {
        // băm token thô nhận từ client
        String hash = sha256Hex(refresh);

        // tìm và khóa token đang hoạt động
        RefreshToken activeToken = refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(hash)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        // đánh dấu thời gian thu hồi
        activeToken.setRevokedAt(Instant.now());

        // lưu thay đổi xuống database
        return refreshTokenRepository.save(activeToken);
    }

    @Override
    public int revokeAllByUserId(UUID userId) {
        return refreshTokenRepository.revokeAllActiveForUser(userId);
    }

    @Override
    public int revokeAllByEmail(String email) {
        return refreshTokenRepository.revokeAllActiveForUser(email);
    }


    //---------------------------------------PRIVATE----------------------------------------//


    private String saveAndGetRefresh(UUID userId) {
        String rawToken = generateSecureRandomToken();
        RefreshToken refreshToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenHash(sha256Hex(rawToken))
                .expiresAt(Instant.now().plus(refreshTokenTTL, ChronoUnit.DAYS))
                .createdAt(Instant.now())
                .build();
        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }


    // Băm chuỗi ký tự bằng thuật toán SHA-256 và định dạng theo chuỗi hexa
    private static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // Sinh chuỗi ngẫu nhiên bảo mật 32 bytes dưới dạng Base64 URL-safe
    private static String generateSecureRandomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

}

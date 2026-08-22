package com.datn.financeapp.auth.service;

import com.datn.financeapp.auth.dto.AuthResponse;
import com.datn.financeapp.auth.dto.LoginRequest;
import com.datn.financeapp.auth.dto.RefreshRequest;
import com.datn.financeapp.auth.dto.RefreshResponse;
import com.datn.financeapp.auth.dto.RegisterRequest;
import com.datn.financeapp.auth.dto.UserSummaryDto;
import com.datn.financeapp.auth.entity.LoginAttempt;
import com.datn.financeapp.auth.entity.RefreshToken;
import com.datn.financeapp.auth.entity.User;
import com.datn.financeapp.auth.repository.LoginAttemptRepository;
import com.datn.financeapp.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.auth.repository.UserRepository;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.security.JwtService;
import com.datn.financeapp.common.wallet.WalletMinimalRepository;
import com.datn.financeapp.common.wallet.WalletMinimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic 4 endpoint lõi vòng đời phiên đăng nhập: AUTH-01 (register), AUTH-02/AUTH-07
 * (login + khoá 5 lần sai/15 phút), AUTH-03 (refresh rotation + reuse detection), AUTH-04
 * (logout). {@code @Transactional} đặt TRÊN TỪNG PUBLIC METHOD (không ở mức class) để phạm vi
 * transaction rõ ràng, tránh nhầm lẫn.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final long REFRESH_TOKEN_TTL_DAYS = 30;
    private static final int MAX_CONSECUTIVE_FAILURES = 5;
    private static final long LOCKOUT_MINUTES = 15;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final WalletMinimalRepository walletMinimalRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * AUTH-01: tạo user + ví "Tiền mặt" số dư 0 trong CÙNG một @Transactional — tránh trường
     * hợp signup nửa vời (có user nhưng không có ví, người dùng mở app lên thấy trống trơn).
     */
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(
                    "EMAIL_ALREADY_EXISTS", HttpStatus.CONFLICT.value(), "Email đã có người dùng.");
        }

        Instant now = Instant.now();
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash(passwordEncoder.encode(req.password()))
                .fullName(req.fullName())
                .plan("free")
                .createdAt(now)
                .build();
        userRepository.save(user);

        WalletMinimal cashWallet = WalletMinimal.builder()
                .id(UUID.randomUUID())
                .userId(user.getId())
                .groupId(null)
                .name("Tiền mặt")
                .type("cash")
                .initialBalance(0L)
                .currentBalance(0L)
                .includeInTotal(true)
                .sortOrder(0)
                .isDeleted(false)
                .createdAt(now)
                .build();
        walletMinimalRepository.save(cashWallet);

        return buildAuthResponse(user);
    }

    /**
     * AUTH-02 + AUTH-07: sai email và sai mật khẩu trả CÙNG mã/message (chống dò danh sách
     * người dùng — T-04-01). Khoá 15 phút sau 5 lần sai liên tiếp; ném ACCOUNT_LOCKED TRƯỚC
     * khi kiểm password và KHÔNG ghi thêm login_attempts khi đã khoá (tránh kéo dài lockout).
     *
     * {@code noRollbackFor = BusinessException.class}: mặc định Spring rollback transaction
     * với MỌI unchecked exception, kể cả BusinessException — nếu để mặc định, bản ghi
     * login_attempts vừa insert (bằng chứng của lần sai) sẽ bị rollback theo cùng lúc với
     * exception INVALID_CREDENTIALS bị ném ra, khiến isLockedOut() không bao giờ đếm đủ 5 lần
     * sai (Rule 1 — phát hiện khi chạy test tích hợp lockout, query luôn trả về rỗng).
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public AuthResponse login(LoginRequest req, String ipAddress, String userAgent) {
        String email = req.email().toLowerCase();

        if (isLockedOut(email)) {
            throw new BusinessException(
                    "ACCOUNT_LOCKED",
                    HttpStatus.FORBIDDEN.value(),
                    "Tài khoản tạm khoá do đăng nhập sai nhiều lần.");
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        boolean passwordOk = userOpt.isPresent()
                && passwordEncoder.matches(req.password(), userOpt.get().getPasswordHash());

        loginAttemptRepository.save(LoginAttempt.builder()
                .id(UUID.randomUUID())
                .email(email)
                .ipAddress(ipAddress)
                .succeeded(passwordOk)
                .userAgent(userAgent)
                .attemptedAt(Instant.now())
                .build());

        if (!passwordOk) {
            throw new BusinessException(
                    "INVALID_CREDENTIALS",
                    HttpStatus.UNAUTHORIZED.value(),
                    "Email hoặc mật khẩu không đúng.");
        }

        User user = userOpt.get();
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        return buildAuthResponse(user);
    }

    /**
     * AUTH-03: rotation dùng một lần + reuse detection, khoá đúng race condition qua
     * {@code SELECT ... FOR UPDATE} (PESSIMISTIC_WRITE).
     *
     * {@code noRollbackFor = BusinessException.class}: nhánh reuse-detection gọi
     * {@code revokeAllActiveForUser} RỒI MỚI throw REFRESH_TOKEN_INVALID — nếu để rollback mặc
     * định, chính hành động thu hồi toàn bộ phiên (mục đích cốt lõi của T-04-03) sẽ bị huỷ
     * theo transaction, khiến token đã đánh cắp vẫn còn active sau khi phát hiện reuse.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public RefreshResponse refresh(RefreshRequest req) {
        String hash = sha256Hex(req.refreshToken());

        Optional<RefreshToken> activeOpt = refreshTokenRepository.findActiveByTokenHashForUpdate(hash);

        if (activeOpt.isEmpty()) {
            // Không tìm thấy token active — có thể chưa từng tồn tại, hoặc đã bị revoke
            // trước đó (dấu hiệu reuse). Cả hai trường hợp trả cùng lỗi để không lộ thông tin.
            refreshTokenRepository
                    .findByTokenHash(hash)
                    .ifPresent(revoked -> refreshTokenRepository.revokeAllActiveForUser(revoked.getUserId()));
            throw new BusinessException(
                    "REFRESH_TOKEN_INVALID", HttpStatus.UNAUTHORIZED.value(), "Thẻ làm mới không hợp lệ.");
        }

        RefreshToken current = activeOpt.get();
        if (current.getExpiresAt().isBefore(Instant.now())) {
            // Hết hạn tự nhiên — không phải dấu hiệu bị đánh cắp, không cần revoke toàn bộ.
            throw new BusinessException(
                    "REFRESH_TOKEN_INVALID", HttpStatus.UNAUTHORIZED.value(), "Thẻ làm mới không hợp lệ.");
        }

        current.setRevokedAt(Instant.now());
        refreshTokenRepository.save(current);

        User user = userRepository
                .findById(current.getUserId())
                .orElseThrow(() -> new BusinessException(
                        "REFRESH_TOKEN_INVALID",
                        HttpStatus.UNAUTHORIZED.value(),
                        "Thẻ làm mới không hợp lệ."));

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getPlan());
        String newRawToken = issueRefreshToken(user.getId());

        return new RefreshResponse(accessToken, newRawToken, jwtService.getAccessTokenExpirySeconds());
    }

    /**
     * AUTH-04: logout idempotent về mặt hành vi — không ném lỗi nếu token không tồn tại/đã
     * revoke, luôn trả 200 rỗng (api/01-XAC-THUC.md mục 4).
     */
    @Transactional
    public void logout(String rawRefreshToken, boolean logoutAllDevices) {
        String hash = sha256Hex(rawRefreshToken);

        if (logoutAllDevices) {
            refreshTokenRepository
                    .findByTokenHash(hash)
                    .ifPresent(token -> refreshTokenRepository.revokeAllActiveForUser(token.getUserId()));
        } else {
            refreshTokenRepository.revokeByTokenHash(hash);
        }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private boolean isLockedOut(String email) {
        List<LoginAttempt> recent = loginAttemptRepository.findTop5ByEmailOrderByAttemptedAtDesc(email);
        if (recent.size() < MAX_CONSECUTIVE_FAILURES) {
            return false;
        }
        boolean allFailed = recent.stream().noneMatch(LoginAttempt::getSucceeded);
        if (!allFailed) {
            return false;
        }
        Instant mostRecentFailure = recent.get(0).getAttemptedAt();
        return mostRecentFailure.isAfter(Instant.now().minus(LOCKOUT_MINUTES, ChronoUnit.MINUTES));
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getPlan());
        String rawRefreshToken = issueRefreshToken(user.getId());

        UserSummaryDto userDto = new UserSummaryDto(
                user.getId(), user.getEmail(), user.getFullName(), user.getAvatarUrl(),
                user.getPlan(), user.getCreatedAt());

        return new AuthResponse(userDto, accessToken, rawRefreshToken, jwtService.getAccessTokenExpirySeconds());
    }

    private String issueRefreshToken(UUID userId) {
        String rawToken = generateSecureRandomToken();
        RefreshToken refreshToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenHash(sha256Hex(rawToken))
                .expiresAt(Instant.now().plus(REFRESH_TOKEN_TTL_DAYS, ChronoUnit.DAYS))
                .createdAt(Instant.now())
                .build();
        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    private static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String generateSecureRandomToken() {
        byte[] bytes = new byte[32]; // 256-bit
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

package com.datn.financeapp.auth.service.impl;

import com.datn.financeapp.auth.dto.request.ForgotPasswordRequest;
import com.datn.financeapp.auth.dto.request.GoogleLoginRequest;
import com.datn.financeapp.auth.dto.request.LoginRequest;
import com.datn.financeapp.auth.dto.request.RefreshRequest;
import com.datn.financeapp.auth.dto.request.RegisterRequest;
import com.datn.financeapp.auth.dto.request.ResendVerificationRequest;
import com.datn.financeapp.auth.dto.request.ResetPasswordRequest;
import com.datn.financeapp.auth.dto.request.VerifyEmailRequest;
import com.datn.financeapp.auth.dto.response.AuthResponse;
import com.datn.financeapp.auth.dto.response.RefreshResponse;
import com.datn.financeapp.auth.dto.response.RegisterResponse;
import com.datn.financeapp.auth.entity.LoginAttempt;
import com.datn.financeapp.auth.entity.OtpModel;
import com.datn.financeapp.auth.entity.PasswordResetToken;
import com.datn.financeapp.auth.entity.RefreshToken;
import com.datn.financeapp.auth.enums.OtpType;
import com.datn.financeapp.auth.exception.*;
import com.datn.financeapp.auth.repository.LoginAttemptRepository;
import com.datn.financeapp.auth.repository.OtpRepository;
import com.datn.financeapp.auth.repository.PasswordResetTokenRepository;
import com.datn.financeapp.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.auth.service.AuthService;
import com.datn.financeapp.auth.service.ForgotPasswordRateLimiter;
import com.datn.financeapp.auth.service.GoogleService;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.mail.EmailService;
import com.datn.financeapp.common.security.JwtService;
import com.datn.financeapp.user.dto.response.UserAccountResponse;
import com.datn.financeapp.user.exception.UserBlockedException;
import com.datn.financeapp.user.exception.UserNotFoundException;
import com.datn.financeapp.user.service.UserAccountService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final long REFRESH_TOKEN_TTL_DAYS = 30;
    private static final int MAX_CONSECUTIVE_FAILURES = 5;
    private static final long LOCKOUT_MINUTES = 15;
    private static final long RESET_CODE_TTL_MINUTES = 15;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserAccountService userAccountService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final ForgotPasswordRateLimiter forgotPasswordRateLimiter;
    private final GoogleService googleIdTokenVerifier;
    private final OtpRepository otpRepository;

    @Transactional
    @Override
    public RegisterResponse register(RegisterRequest req) {
        String email = req.email().toLowerCase().trim();
        if (userAccountService.existsByEmail(email)) {
            throw new AuthEmailAlreadyExistsException();
        }

        Instant now = Instant.now();
        UserAccountResponse user = userAccountService.createEmailUser(email, req.password(), now);

        String otp = generateNumericOtp();
        otpRepository.save(OtpModel.builder()
                .email(email)
                .type(OtpType.REGISTER_OTP)
                .code(otp)
                .ttl(15L)
                .createdAt(now)
                .build());

        emailService.sendVerificationOtp(email, otp);

        return new RegisterResponse(
                user.id(),
                user.email()
        );
    }

    @Transactional
    @Override
    public AuthResponse verifyEmail(VerifyEmailRequest req) {
        String email = req.email().toLowerCase().trim();

        OtpModel otp = otpRepository.findByTypeAndEmail(OtpType.REGISTER_OTP, email)
                .orElseThrow(AuthVerificationCodeInvalidException::new);

        if (!otp.getCode().equals(req.code().trim())) {
            throw new AuthVerificationCodeInvalidException();
        }

        userAccountService.confirmUserEmail(email);
        otpRepository.deleteByTypeAndEmail(OtpType.REGISTER_OTP, email);

        UserAccountResponse user = userAccountService.findForAuthByEmail(email)
                .filter(u -> !u.isDeleted())
                .orElseThrow(UserNotFoundException::new);

        if (user.isBlocked()) {
            throw new UserBlockedException();
        }

        userAccountService.recordLoginSuccess(user.id(), Instant.now());
        return buildAuthResponse(user);
    }

    @Transactional
    @Override
    public void resendVerification(ResendVerificationRequest req) {
        String email = req.email().toLowerCase().trim();

        Optional<UserAccountResponse> userOpt = userAccountService.findForAuthByEmail(email)
                .filter(u -> !u.isDeleted());
        if (userOpt.isEmpty()) {
            return;
        }

        UserAccountResponse user = userOpt.get();
        if (user.isConfirm()) {
            throw new BusinessException(ErrorCode.ACCOUNT_ALREADY_VERIFIED);
        }
        if (user.isBlocked()) {
            throw new UserBlockedException();
        }

        Instant now = Instant.now();
        Optional<OtpModel> existingOtp = otpRepository.findById(email);
        if (existingOtp.isPresent() && existingOtp.get().getCreatedAt() != null) {
            long secondsSinceLast = Duration.between(existingOtp.get().getCreatedAt(), now).getSeconds();
            if (secondsSinceLast < 60) {
                throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED, "Vui lòng chờ 60 giây trước khi yêu cầu gửi lại mã.");
            }
        }

        String otp = generateNumericOtp();
        otpRepository.save(OtpModel.builder()
                .email(email)
                .type(OtpType.REGISTER_OTP)
                .code(otp)
                .ttl(15L)
                .createdAt(now)
                .build());

        emailService.sendVerificationOtp(email, otp);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    @Override
    public AuthResponse login(LoginRequest req, String ipAddress, String userAgent) {
        String email = req.email().toLowerCase();

        if (isLockedOut(email))
            throw new AuthAccountLockedException();


        Optional<UserAccountResponse> userOpt = userAccountService.findForAuthByEmail(email)
                .filter(u -> !u.isDeleted());

        boolean passwordOk = userOpt.isPresent()
                && userOpt.get().password() != null
                && passwordEncoder.matches(req.password(), userOpt.get().password());

        loginAttemptRepository.save(LoginAttempt.builder()
                .id(UUID.randomUUID())
                .email(email)
                .ipAddress(ipAddress)
                .succeeded(passwordOk)
                .userAgent(userAgent)
                .attemptedAt(Instant.now())
                .build());

        if (!passwordOk) {
            throw new AuthInvalidCredentialsException();
        }

        UserAccountResponse user = userOpt.get();

        if (!user.isConfirm()) { // hoặc user.status() == UserStatus.PENDING_VERIFY
            throw new AuthAccountNotVerifiedException();
        }

        if (user.isBlocked()) {
            throw new UserBlockedException();
        }

        userAccountService.recordLoginSuccess(user.id(), Instant.now());
        return buildAuthResponse(user);
    }

    @Transactional
    @Override
    public AuthResponse loginWithGoogle(GoogleLoginRequest req) {
        GoogleService.GoogleUserInfo info = googleIdTokenVerifier.verify(req.idToken());
        String email = info.email().toLowerCase();
        Instant now = Instant.now();

        Optional<UserAccountResponse> byGoogleId = userAccountService.findForAuthByGoogleId(info.googleId());
        if (byGoogleId.isPresent()) {
            UserAccountResponse user = byGoogleId.get();
            validateGoogleAccountState(user);
            userAccountService.recordLoginSuccess(user.id(), now);
            return buildAuthResponse(user);
        }

        Optional<UserAccountResponse> byEmail = userAccountService.findForAuthByEmail(email);
        if (byEmail.isPresent()) {
            UserAccountResponse user = byEmail.get();
            validateGoogleAccountState(user);
            return buildAuthResponse(userAccountService
                    .linkGoogleAccount(
                            user.id(),
                            info.googleId(),
                            now)
            );
        }
        return buildAuthResponse(userAccountService.createGoogleUser(email, info.googleId(), now));
    }

    @Transactional(noRollbackFor = BusinessException.class)
    @Override
    public RefreshResponse refresh(RefreshRequest req) {
        String hash = sha256Hex(req.refreshToken());
        Optional<RefreshToken> activeOpt = refreshTokenRepository.findActiveByTokenHashForUpdate(hash);

        if (activeOpt.isEmpty()) {
            refreshTokenRepository
                    .findByTokenHash(hash)
                    .ifPresent(revoked -> refreshTokenRepository.revokeAllActiveForUser(revoked.getUserId()));
            throw new AuthRefreshTokenInvalidException();
        }

        RefreshToken current = activeOpt.get();
        if (current.getExpiresAt().isBefore(Instant.now())) {
            throw new AuthRefreshTokenInvalidException();
        }

        current.setRevokedAt(Instant.now());
        refreshTokenRepository.save(current);

        UserAccountResponse user = userAccountService.findActiveSummaryById(current.getUserId())
                .orElseThrow(AuthRefreshTokenInvalidException::new);

        String accessToken = jwtService.generateAccessToken(user.id(), user.plan().name());
        String newRawToken = issueRefreshToken(user.id());

        return new RefreshResponse(accessToken, newRawToken, jwtService.getAccessTokenExpirySeconds());
    }

    @Transactional
    @Override
    public void logout(UUID userId, String rawRefreshToken, boolean logoutAllDevices) {
        if (logoutAllDevices) {
            refreshTokenRepository.revokeAllActiveForUser(userId);
            return;
        }

        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        refreshTokenRepository.revokeByTokenHash(sha256Hex(rawRefreshToken));
    }

    @Transactional
    @Override
    public void forgotPassword(ForgotPasswordRequest req) {
        forgotPasswordRateLimiter.consume(req.email());

        Optional<UUID> userIdOpt = userAccountService.findUserIdForPasswordReset(req.email());
        if (userIdOpt.isEmpty()) {
            return;
        }

        UUID userId = userIdOpt.get();
        String rawResetCode = generateNumericOtp();
        PasswordResetToken token = PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenHash(sha256Hex(rawResetCode))
                .expiresAt(Instant.now().plus(RESET_CODE_TTL_MINUTES, ChronoUnit.MINUTES))
                .createdAt(Instant.now())
                .build();
        passwordResetTokenRepository.save(token);

        emailService.sendPasswordResetCode(req.email(), rawResetCode);
    }

    @Transactional
    @Override
    public void resetPassword(ResetPasswordRequest req) {
        String hash = sha256Hex(req.resetCode());
        PasswordResetToken token = passwordResetTokenRepository
                .findByTokenHashAndUsedAtIsNull(hash)
                .orElseThrow(AuthResetCodeInvalidException::new);

        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new AuthResetCodeInvalidException();
        }

        try {
            userAccountService.resetPasswordWithCode(token.getUserId(), req.newPassword());
        } catch (UserNotFoundException e) {
            throw new AuthResetCodeInvalidException();
        }

        token.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(token);

        refreshTokenRepository.revokeAllActiveForUser(token.getUserId());
    }

    @Transactional
    @Override
    public void revokeAllTokensForUser(UUID userId) {
        refreshTokenRepository.revokeAllActiveForUser(userId);
    }

    // Kiểm tra trạng thái tài khoản khi đăng nhập bằng Google (chặn tài khoản đã bị xoá hoặc bị khoá)
    private void validateGoogleAccountState(UserAccountResponse user) {
        if (user.isDeleted()) {
            throw new AuthInvalidCredentialsException();
        }
        if (user.isBlocked()) {
            throw new UserBlockedException();
        }
    }

    // Kiểm tra tài khoản có đang bị tạm khoá đăng nhập (5 lần thất bại liên tiếp trong vòng 15 phút)
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

    // Tạo AuthResponse kèm cặp Access Token (JWT) và Refresh Token mới
    private AuthResponse buildAuthResponse(UserAccountResponse user) {
        String accessToken = jwtService.generateAccessToken(user.id(), user.plan().name());
        String rawRefreshToken = issueRefreshToken(user.id());
        return new AuthResponse(user, accessToken, rawRefreshToken, jwtService.getAccessTokenExpirySeconds());
    }

    // Sinh Refresh Token ngẫu nhiên, lưu mã băm SHA-256 vào CSDL và trả về chuỗi token gốc
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

    // Sinh mã gồm 6 chữ số ngẫu nhiên
    private static String generateNumericOtp() {
        return String.format("%06d", new SecureRandom().nextInt(1_000_000));
    }

    // Sinh chuỗi ngẫu nhiên bảo mật 32 bytes dưới dạng Base64 URL-safe
    private static String generateSecureRandomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

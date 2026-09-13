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
import com.datn.financeapp.auth.entity.RefreshToken;
import com.datn.financeapp.auth.enums.OtpType;
import com.datn.financeapp.auth.events.LoginSuccessEvent;
import com.datn.financeapp.auth.events.VerifyEmailEvent;
import com.datn.financeapp.auth.exception.*;
import com.datn.financeapp.auth.repository.LoginAttemptRepository;
import com.datn.financeapp.auth.repository.OtpRepository;
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
import com.datn.financeapp.user.service.AccountService;

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
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final long REFRESH_TOKEN_TTL_DAYS = 30;
    private static final int MAX_CONSECUTIVE_FAILURES = 5;
    private static final long LOCKOUT_MINUTES = 15;
    private static final long RESET_CODE_TTL_MINUTES = 15;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AccountService userAccountService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final ForgotPasswordRateLimiter forgotPasswordRateLimiter;
    private final GoogleService googleIdTokenVerifier;
    private final OtpRepository otpRepository;

    private final ApplicationEventPublisher eventPublisher;

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

        eventPublisher.publishEvent(new VerifyEmailEvent(email));


//        userAccountService.confirmUserEmail(email);
        otpRepository.deleteByTypeAndEmail(OtpType.REGISTER_OTP, email);

        var user = userAccountService.findByEmail(email);
        assertUsable(user);

        eventPublisher.publishEvent(new LoginSuccessEvent(user.id(), Instant.now()));
        return buildAuthResponse(user);
    }

    @Transactional
    @Override
    public void resendVerification(ResendVerificationRequest req) {
        String email = req.email().toLowerCase().trim();
        Instant now = Instant.now();

        // Tài khoản đã xoá hoặc bị khoá im lặng như email chưa đăng ký — không gửi mã, không báo
        // lỗi, để người gửi không suy ra được trạng thái tài khoản.
        var u = userAccountService.findByEmail(email);
        if (u == null || u.isDeleted() || u.isBlocked()) {
            return;
        }
        if (u.isConfirm()) {
            throw new BusinessException(ErrorCode.ACCOUNT_ALREADY_VERIFIED);
        }


        // Phải tra bằng findByTypeAndEmail: khoá Redis thật là "register_otp:<email>"
        // (OtpModel.buildId), nên findById(email) luôn trả rỗng và cơ chế chờ 60 giây bên dưới
        // không bao giờ chạy — gửi lại mã bao nhiêu lần cũng được.
        Optional<OtpModel> existingOtp = otpRepository.findByTypeAndEmail(OtpType.REGISTER_OTP, email);
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
        String email = req.email().toLowerCase().trim();

        if (isLockedOut(email))
            throw new AuthAccountLockedException();


        var user = userAccountService.findByEmail(email);

        boolean passwordOk = user != null
                && !user.isDeleted()
                && user.password() != null
                && passwordEncoder.matches(req.password(), user.password());

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

        // Chỉ người gõ ĐÚNG mật khẩu mới đáng được biết tài khoản đang bị khoá — sai mật khẩu thì
        // đã trả INVALID_CREDENTIALS ở trên, không xác nhận email đó có tồn tại hay không.
        // Kiểm ở đây chứ không ở findByEmail: xem Javadoc AccountServiceImpl.findByEmail.
        if (user.isBlocked()) {
            throw new UserBlockedException();
        }

        if (user.notConfirm()) { // hoặc user.status() == UserStatus.PENDING_VERIFY
            throw new AuthAccountNotVerifiedException();
        }

        eventPublisher.publishEvent(new LoginSuccessEvent(user.id(), Instant.now()));
        return buildAuthResponse(user);
    }

    @Transactional
    @Override
    public AuthResponse loginWithGoogle(GoogleLoginRequest req) {
        GoogleService.GoogleUserInfo info = googleIdTokenVerifier.verify(req.idToken());
        String email = info.email().toLowerCase().trim();
        Instant now = Instant.now();

        // 1. Tìm hoặc tạo user
        var user = userAccountService.findByGoogleId(info.googleId());

        if (user == null) {
            var byEmail = userAccountService.findByEmail(email);
            if (byEmail != null) {
                // Đã có tài khoản bằng email -> liên kết với Google ID
                assertUsable(byEmail);
                user = userAccountService.linkGoogleAccount(byEmail.id(), info.googleId(), now);
            } else {
                // Chưa từng có tài khoản -> tạo mới
                user = userAccountService.createGoogleUser(email, info.googleId(), now);
            }
        } else {
            assertUsable(user);
        }

        // 2. Bắn event đăng nhập thành công (đồng bộ với hàm login thường)
        eventPublisher.publishEvent(new LoginSuccessEvent(user.id(), now));

        // 3. Trả về response
        return buildAuthResponse(user);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    @Override
    public RefreshResponse refresh(RefreshRequest req) {
        log.info(req.refreshToken());
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

        var authorities = userAccountService.findAuthoritiesByUserId(user.id());
        int topRoleLevel = userAccountService.findTopRoleLevel(user.id());
        String accessToken = jwtService.generateAccessToken(user.id(), user.plan().name(), authorities, topRoleLevel);
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
        String email = req.email().toLowerCase().trim();
        forgotPasswordRateLimiter.consume(email);

        Optional<UUID> userIdOpt = userAccountService.findUserIdForPasswordReset(email);
        if (userIdOpt.isEmpty()) {
            return;
        }

        String rawResetCode = generateNumericOtp();
        Instant now = Instant.now();
        otpRepository.save(OtpModel.builder()
                .email(email)
                .type(OtpType.PASSWORD_RESET_OTP)
                .code(rawResetCode)
                .ttl(RESET_CODE_TTL_MINUTES)
                .createdAt(now)
                .build());

        emailService.sendPasswordResetCode(email, rawResetCode);
    }

    @Transactional
    @Override
    public void resetPassword(ResetPasswordRequest req) {
        String email = req.email().toLowerCase().trim();
        OtpModel otp = otpRepository.findByTypeAndEmail(OtpType.PASSWORD_RESET_OTP, email)
                .orElseThrow(AuthResetCodeInvalidException::new);

        if (!otp.getCode().equals(req.resetCode().trim())) {
            throw new AuthResetCodeInvalidException();
        }

        var user = userAccountService.findByEmail(email);

        // Tài khoản bị khoá HOẶC đã xoá đều trả RESET_CODE_INVALID như mã sai thường — mã lỗi
        // riêng sẽ xác nhận email đó có đăng ký và đang ở trạng thái nào.
        if (user == null || user.isDeleted() || user.isBlocked()) {
            throw new AuthResetCodeInvalidException();
        }

        try {
            userAccountService.resetPasswordWithCode(user.id(), req.newPassword());
        } catch (UserNotFoundException e) {
            throw new AuthResetCodeInvalidException();
        }

        otpRepository.deleteByTypeAndEmail(OtpType.PASSWORD_RESET_OTP, email);
        refreshTokenRepository.revokeAllActiveForUser(user.id());
    }

    @Transactional
    @Override
    public void revokeAllTokensForUser(UUID userId) {
        refreshTokenRepository.revokeAllActiveForUser(userId);
    }

    // Kiểm tra tài khoản có đang bị tạm khoá đăng nhập (5 lần thất bại liên tiếp trong vòng 15 phút)
    /**
     * Chặn tài khoản đã xoá hoặc bị khoá ở những luồng mà danh tính đã được chứng minh sẵn
     * (đăng nhập Google, xác thực email) — khác {@code login} bằng mật khẩu, nơi phải trả
     * {@code INVALID_CREDENTIALS} trước để không xác nhận email có tồn tại.
     *
     * <p>Tài khoản đã xoá cố tình trả {@code INVALID_CREDENTIALS} chứ không phải mã riêng: với
     * thế giới bên ngoài nó phải giống hệt một email chưa từng đăng ký.
     */
    private void assertUsable(UserAccountResponse user) {
        if (user.isDeleted()) {
            throw new AuthInvalidCredentialsException();
        }
        if (user.isBlocked()) {
            throw new UserBlockedException();
        }
    }

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
        var authorities = userAccountService.findAuthoritiesByUserId(user.id());
        int topRoleLevel = userAccountService.findTopRoleLevel(user.id());
        String accessToken = jwtService.generateAccessToken(user.id(), user.plan().name(), authorities, topRoleLevel);
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

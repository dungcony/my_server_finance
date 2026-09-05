package com.datn.financeapp.auth.service;

import com.datn.financeapp.auth.dto.AuthResponse;
import com.datn.financeapp.auth.dto.ChangePasswordRequest;
import com.datn.financeapp.auth.dto.ForgotPasswordRequest;
import com.datn.financeapp.auth.dto.LoginRequest;
import com.datn.financeapp.auth.dto.RefreshRequest;
import com.datn.financeapp.auth.dto.RefreshResponse;
import com.datn.financeapp.auth.dto.RegisterRequest;
import com.datn.financeapp.auth.dto.ResetPasswordRequest;
import com.datn.financeapp.auth.dto.UpdateProfileRequest;
import com.datn.financeapp.auth.dto.UserDetailDto;
import com.datn.financeapp.auth.dto.UserStatsDto;
import com.datn.financeapp.auth.dto.UserSummaryDto;
import com.datn.financeapp.auth.entity.LoginAttempt;
import com.datn.financeapp.auth.entity.PasswordResetToken;
import com.datn.financeapp.auth.entity.RefreshToken;
import com.datn.financeapp.auth.entity.User;
import com.datn.financeapp.auth.repository.LoginAttemptRepository;
import com.datn.financeapp.auth.repository.PasswordResetTokenRepository;
import com.datn.financeapp.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.auth.repository.UserRepository;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.security.JwtService;
import com.datn.financeapp.wallet.entity.Wallet;
import com.datn.financeapp.wallet.repository.WalletRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final WalletRepository walletRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PasswordResetNotifier passwordResetNotifier;
    private final JdbcTemplate jdbcTemplate;

    private static final long RESET_CODE_TTL_MINUTES = 15;

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
                .username(req.username())
                .plan("free")
                // Bốn cột V12 nêu tường minh dù entity đã có @Builder.Default — luồng đăng ký
                // bằng email là chỗ duy nhất quyết định giá trị khởi đầu của chúng, đọc thẳng ở
                // đây rẻ hơn phải mở entity ra tra. isConfirm = false vì luồng xác thực email
                // chưa làm (prd/01 mục 11); tài khoản Google sau này sẽ đặt true.
                .role("USER")
                .isConfirm(false)
                .isBlocked(false)
                .isDeleted(false)
                .createdAt(now)
                .build();
        userRepository.save(user);

        Wallet cashWallet = Wallet.builder()
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
        walletRepository.save(cashWallet);

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

        // B3 — tài khoản đã xoá coi như KHÔNG TỒN TẠI với thế giới bên ngoài. Bỏ nó ra khỏi
        // userOpt ngay tại đây để mọi nhánh phía dưới hành xử y hệt trường hợp email chưa từng
        // đăng ký: cùng mã INVALID_CREDENTIALS, cùng bản ghi login_attempts. Báo "tài khoản đã
        // xoá" là xác nhận email đó từng đăng ký — vẫn là rò rỉ thông tin (prd/01 mục 7.1).
        userOpt = userOpt.filter(u -> !u.isDeleted());

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

        // B3 — ADMIN khoá: mã RIÊNG với ACCOUNT_LOCKED (khoá tạm 15 phút) vì hai tình huống cần
        // hai hành động khác nhau — chờ hết giờ, hay liên hệ hỗ trợ (prd/01 mục 3). Kiểm SAU khi
        // mật khẩu đúng: người gõ sai mật khẩu của một tài khoản bị khoá không cần biết tài
        // khoản đó tồn tại và đang bị khoá.
        if (user.isBlocked()) {
            throw new BusinessException(
                    "ACCOUNT_BLOCKED",
                    HttpStatus.FORBIDDEN.value(),
                    "Tài khoản đã bị khoá. Vui lòng liên hệ hỗ trợ.");
        }

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

        // B3 — chặn cả tài khoản bị khoá lẫn đã xoá: refresh token còn nằm trong máy người dùng
        // và sống 30 ngày, không chặn ở đây thì họ xin access token mới mãi mãi và việc khoá tài
        // khoản thành vô nghĩa. Trả cùng REFRESH_TOKEN_INVALID như token hỏng — app đã biết cách
        // xử lý mã này (đá về màn đăng nhập), và ở đó mới hiện lý do thật.
        User user = userRepository
                .findById(current.getUserId())
                .filter(u -> !u.isBlocked() && !u.isDeleted())
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
    public void logout(UUID userId, String rawRefreshToken, boolean logoutAllDevices) {
        // api/01 mục 4 KHÔNG đặc tả trường mang refresh token trong body — client hoàn toàn có
        // thể gọi logout mà chỉ gửi {@code logout_all_devices}, và app Flutter đang làm đúng như
        // vậy. Trước đây nhánh này băm thẳng null và ném NullPointerException, khiến endpoint
        // trả 500: người dùng bấm Đăng xuất thì app kẹt lại màn cũ vì lời gọi mạng ném lỗi
        // trước khi kịp xoá trạng thái đăng nhập (FIX-01, đợt test 02/09/2026).
        //
        // {@code userId} lấy từ JWT chứ không suy ra từ body: endpoint này bắt buộc xác thực nên
        // danh tính luôn có sẵn và đáng tin hơn hẳn một trường tuỳ chọn do client gửi lên.
        if (logoutAllDevices) {
            refreshTokenRepository.revokeAllActiveForUser(userId);
            return;
        }

        // Không gửi token thì không có gì để thu hồi riêng lẻ — phiên phía máy chủ tự hết hạn
        // theo TTL, còn thẻ trên máy người dùng đã bị client xoá. Đúng tinh thần "idempotent về
        // mặt hành vi" ghi ở javadoc trên: không ném lỗi, luôn trả 200.
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        refreshTokenRepository.revokeByTokenHash(sha256Hex(rawRefreshToken));
    }

    /**
     * AUTH-05: GET /auth/me — hồ sơ đầy đủ kèm {@code stats}. Phase 1 chưa có repository riêng
     * cho {@code transactions}/{@code group_members} (module nghiệp vụ thuộc Phase 2/5+) — dùng
     * native {@code COUNT(*)} trực tiếp lên bảng đã có sẵn từ V1/V2, luôn trả số thật (0 nếu
     * chưa có dữ liệu), không lỗi, không null.
     */
    @Transactional(readOnly = true)
    public UserDetailDto getMe(UUID userId) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(
                        "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy tài khoản."));

        long walletCount = walletRepository.countByUserIdAndIsDeletedFalse(userId);
        Long transactionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE user_id = ? AND NOT is_deleted",
                Long.class, userId);
        Long groupCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM group_members WHERE user_id = ? AND is_active",
                Long.class, userId);

        UserStatsDto stats = new UserStatsDto(
                walletCount, transactionCount == null ? 0 : transactionCount,
                groupCount == null ? 0 : groupCount);

        return new UserDetailDto(
                user.getId(), user.getEmail(), user.getUsername(), user.getAvatarUrl(),
                user.getPlan(), user.isConfirm(), user.getRole(),
                user.getCreatedAt(), user.getLastLoginAt(), stats);
    }

    /**
     * AUTH-05: PATCH /auth/me — chỉ đổi được {@code fullName}/{@code avatarUrl} (kiểu dữ liệu
     * DTO không có field email/plan nên không có cách nào truyền lên). Field null trong request
     * nghĩa là "không đổi" (PATCH bán phần).
     */
    @Transactional
    public UserSummaryDto updateProfile(UUID userId, UpdateProfileRequest req) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(
                        "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy tài khoản."));

        if (req.username() != null) {
            user.setUsername(req.username());
        }
        if (req.avatarUrl() != null) {
            user.setAvatarUrl(req.avatarUrl());
        }
        userRepository.save(user);

        return new UserSummaryDto(
                user.getId(), user.getEmail(), user.getUsername(), user.getAvatarUrl(),
                user.getPlan(), user.isConfirm(), user.getRole(), user.getCreatedAt());
    }

    /**
     * AUTH-06: POST /auth/change-password.
     *
     * <p><b>Quyết định D-24 về "phiên hiện tại":</b> {@code api/01-XAC-THUC.md} mục 7 chỉ nhận
     * {@code old_password}/{@code new_password} trong body, KHÔNG nhận refresh token. Request
     * không mang refresh token nào để "giữ lại" — do đó revoke TOÀN BỘ refresh token của user,
     * không có ngoại lệ. Vẫn an toàn hơn đặc tả gốc (chặt hơn, không vi phạm): access token hiện
     * tại (1h) còn dùng được tới khi hết hạn tự nhiên, người dùng chỉ không refresh được nữa.
     */
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest req) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(
                        "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy tài khoản."));

        if (!passwordEncoder.matches(req.oldPassword(), user.getPasswordHash())) {
            throw new BusinessException(
                    "WRONG_OLD_PASSWORD", HttpStatus.BAD_REQUEST.value(), "Mật khẩu cũ không đúng.");
        }

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);

        // T-05-03: gọi lại đúng method Plan 04 viết sẵn, KHÔNG viết lại logic revoke.
        refreshTokenRepository.revokeAllActiveForUser(userId);
    }

    /**
     * AUTH-06: POST /auth/forgot-password. T-05-01 (mitigate): CẢ HAI nhánh (email tồn tại/không
     * tồn tại) đều return void, không throw — Controller luôn trả 200 với message giống hệt
     * nhau. Nhánh "không tồn tại" không tạo token, không gọi notifier.
     */
    @Transactional
    public void forgotPassword(ForgotPasswordRequest req) {
        // B3 — LỖ HỔNG ĐÃ VÁ: trước đây chỉ findByEmail không lọc gì, nên tài khoản đã xoá vẫn
        // nhận mã và đặt lại mật khẩu thành công — người đã rời đi vẫn quay lại được. Vẫn trả
        // 200 âm thầm (không token, không mail) chứ không trả 403: trả lỗi ở đây cho kẻ xấu một
        // cách dò xem email nào đã bị khoá (prd/01 mục 7.1).
        Optional<User> userOpt = userRepository
                .findByEmail(req.email().toLowerCase())
                .filter(u -> !u.isBlocked() && !u.isDeleted());
        if (userOpt.isEmpty()) {
            return;
        }

        User user = userOpt.get();
        String rawResetCode = generateResetCode();
        PasswordResetToken token = PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .userId(user.getId())
                .tokenHash(sha256Hex(rawResetCode))
                .expiresAt(Instant.now().plus(RESET_CODE_TTL_MINUTES, ChronoUnit.MINUTES))
                .createdAt(Instant.now())
                .build();
        passwordResetTokenRepository.save(token);

        passwordResetNotifier.sendResetCode(user.getEmail(), rawResetCode);
    }

    /**
     * AUTH-06: POST /auth/reset-password. Mã sai/hết hạn/đã dùng đều trả cùng lỗi
     * {@code RESET_CODE_INVALID} (không phân biệt "sai" và "hết hạn" theo đúng đặc tả gộp chung
     * 1 mã). T-05-04 (mitigate): đánh dấu {@code usedAt} trong CÙNG transaction với đổi mật
     * khẩu — mã không thể dùng lại lần 2.
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        String hash = sha256Hex(req.resetCode());
        PasswordResetToken token = passwordResetTokenRepository
                .findByTokenHashAndUsedAtIsNull(hash)
                .orElseThrow(() -> new BusinessException(
                        "RESET_CODE_INVALID", HttpStatus.BAD_REQUEST.value(),
                        "Mã sai, hết hạn hoặc đã dùng."));

        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(
                    "RESET_CODE_INVALID", HttpStatus.BAD_REQUEST.value(),
                    "Mã sai, hết hạn hoặc đã dùng.");
        }

        // B3 — cửa thứ tư. Mã có thể đã phát hợp lệ TRƯỚC khi ADMIN khoá tài khoản, nên phải
        // kiểm lại tại thời điểm dùng chứ không chỉ tại thời điểm phát. Dùng chung
        // RESET_CODE_INVALID với mọi lý do khác để không lộ trạng thái tài khoản.
        User user = userRepository
                .findById(token.getUserId())
                .filter(u -> !u.isBlocked() && !u.isDeleted())
                .orElseThrow(() -> new BusinessException(
                        "RESET_CODE_INVALID", HttpStatus.BAD_REQUEST.value(),
                        "Mã sai, hết hạn hoặc đã dùng."));
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);

        token.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(token);

        // api/01 mục 9: "Thành công thì thu hồi toàn bộ thẻ của tài khoản" — không có ngoại lệ
        // "trừ phiên hiện tại" ở đây, khác với change-password.
        refreshTokenRepository.revokeAllActiveForUser(user.getId());
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
                user.getId(), user.getEmail(), user.getUsername(), user.getAvatarUrl(),
                user.getPlan(), user.isConfirm(), user.getRole(), user.getCreatedAt());

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

    /**
     * Mã đặt lại là 6 chữ số vì người dùng phải GÕ TAY nó từ email vào màn hình điện thoại —
     * chuỗi Base64 256-bit của {@link #generateSecureRandomToken()} an toàn hơn nhưng không ai
     * gõ nổi 43 ký tự. Không gian mã nhỏ (10^6) được bù bằng ba lớp: mã sống 15 phút, dùng một
     * lần, và rate limit forgot-password theo email.
     */
    private static String generateResetCode() {
        return String.format("%06d", new SecureRandom().nextInt(1_000_000));
    }

    private static String generateSecureRandomToken() {
        byte[] bytes = new byte[32]; // 256-bit
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

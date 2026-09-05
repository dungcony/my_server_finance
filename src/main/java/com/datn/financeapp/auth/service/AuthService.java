package com.datn.financeapp.auth.service;

import com.datn.financeapp.auth.dto.response.AuthResponse;
import com.datn.financeapp.auth.dto.request.ChangePasswordRequest;
import com.datn.financeapp.auth.dto.request.DeleteAccountRequest;
import com.datn.financeapp.auth.dto.request.ForgotPasswordRequest;
import com.datn.financeapp.auth.dto.request.GoogleLoginRequest;
import com.datn.financeapp.auth.dto.request.LoginRequest;
import com.datn.financeapp.auth.dto.request.RefreshRequest;
import com.datn.financeapp.auth.dto.response.RefreshResponse;
import com.datn.financeapp.auth.dto.request.RegisterRequest;
import com.datn.financeapp.auth.dto.request.ResetPasswordRequest;
import com.datn.financeapp.auth.dto.request.UpdateProfileRequest;
import com.datn.financeapp.auth.dto.response.UserDetailResponse;
import com.datn.financeapp.auth.dto.response.UserStatsResponse;
import com.datn.financeapp.auth.dto.response.UserSummaryResponse;
import com.datn.financeapp.auth.entity.LoginAttempt;
import com.datn.financeapp.auth.entity.PasswordResetToken;
import com.datn.financeapp.auth.entity.RefreshToken;
import com.datn.financeapp.auth.entity.User;
import com.datn.financeapp.auth.repository.LoginAttemptRepository;
import com.datn.financeapp.auth.repository.PasswordResetTokenRepository;
import com.datn.financeapp.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.auth.repository.UserRepository;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.security.JwtService;
import com.datn.financeapp.wallet.service.WalletService;
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
    private final WalletService walletService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PasswordResetNotifier passwordResetNotifier;
    private final ForgotPasswordRateLimiter forgotPasswordRateLimiter;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;
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
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
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
        walletService.createDefaultCashWallet(user.getId(), now);

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
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        Optional<User> userOpt = userRepository.findByEmail(email);

        // B3 — tài khoản đã xoá coi như KHÔNG TỒN TẠI với thế giới bên ngoài. Bỏ nó ra khỏi
        // userOpt ngay tại đây để mọi nhánh phía dưới hành xử y hệt trường hợp email chưa từng
        // đăng ký: cùng mã INVALID_CREDENTIALS, cùng bản ghi login_attempts. Báo "tài khoản đã
        // xoá" là xác nhận email đó từng đăng ký — vẫn là rò rỉ thông tin (prd/01 mục 7.1).
        userOpt = userOpt.filter(u -> !u.isDeleted());

        // D3 — password_hash có thể NULL từ V13 (tài khoản Google thuần). Không kiểm null thì
        // passwordEncoder.matches ném NullPointerException, trả 500 thay vì INVALID_CREDENTIALS.
        // Tài khoản chưa từng đặt mật khẩu thì không có mật khẩu nào khớp được — hành xử y hệt
        // sai mật khẩu, kể cả bản ghi login_attempts (api/01 mục 13).
        boolean passwordOk = userOpt.isPresent()
                && userOpt.get().getPasswordHash() != null
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
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        User user = userOpt.get();

        // B3 — ADMIN khoá: mã RIÊNG với ACCOUNT_LOCKED (khoá tạm 15 phút) vì hai tình huống cần
        // hai hành động khác nhau — chờ hết giờ, hay liên hệ hỗ trợ (prd/01 mục 3). Kiểm SAU khi
        // mật khẩu đúng: người gõ sai mật khẩu của một tài khoản bị khoá không cần biết tài
        // khoản đó tồn tại và đang bị khoá.
        if (user.isBlocked()) {
            throw new BusinessException(ErrorCode.ACCOUNT_BLOCKED);
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
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        RefreshToken current = activeOpt.get();
        if (current.getExpiresAt().isBefore(Instant.now())) {
            // Hết hạn tự nhiên — không phải dấu hiệu bị đánh cắp, không cần revoke toàn bộ.
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
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
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));

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
    public UserDetailResponse getMe(UUID userId) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy tài khoản."));

        long walletCount = walletService.countActiveWallets(userId);
        Long transactionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE user_id = ? AND NOT is_deleted",
                Long.class, userId);
        Long groupCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM group_members WHERE user_id = ? AND is_active",
                Long.class, userId);

        UserStatsResponse stats = new UserStatsResponse(
                walletCount, transactionCount == null ? 0 : transactionCount,
                groupCount == null ? 0 : groupCount);

        return new UserDetailResponse(
                user.getId(), user.getEmail(), user.getUsername(), user.getAvatarUrl(),
                user.getPlan(), user.isConfirm(), user.getRole(),
                user.getPasswordHash() != null, user.getGoogleId() != null,
                user.getCreatedAt(), user.getLastLoginAt(), stats);
    }

    /**
     * AUTH-05: PATCH /auth/me — chỉ đổi được {@code fullName}/{@code avatarUrl} (kiểu dữ liệu
     * DTO không có field email/plan nên không có cách nào truyền lên). Field null trong request
     * nghĩa là "không đổi" (PATCH bán phần).
     */
    @Transactional
    public UserSummaryResponse updateProfile(UUID userId, UpdateProfileRequest req) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy tài khoản."));

        if (req.username() != null) {
            user.setUsername(req.username());
        }
        if (req.avatarUrl() != null) {
            user.setAvatarUrl(req.avatarUrl());
        }
        userRepository.save(user);

        return toSummary(user);
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
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy tài khoản."));

        // D3 — tài khoản Google thuần chưa từng đặt mật khẩu, không có "mật khẩu cũ" để nhập.
        // App đã ẩn nút Đổi mật khẩu với loại tài khoản này, nhưng backend vẫn phải tự chặn:
        // bảo vệ chỉ ở phía client thì ai gọi thẳng API cũng qua (api/01 mục 13).
        if (user.getPasswordHash() == null) {
            throw new BusinessException(ErrorCode.NO_PASSWORD_SET);
        }

        if (!passwordEncoder.matches(req.oldPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.WRONG_OLD_PASSWORD);
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
        // C4 — tầng giới hạn theo email, tiêu lượt TRƯỚC khi tra email có tồn tại hay không
        // (api/01 mục 11). Đây là lời gọi duy nhất có thể ném ra khỏi method, cố ý: ngoại lệ
        // "luôn trả 200" nói ở javadoc trên chỉ áp cho việc email tồn tại hay không.
        forgotPasswordRateLimiter.consume(req.email());

        // B3 — LỖ HỔNG ĐÃ VÁ: trước đây chỉ findByEmail không lọc gì, nên tài khoản đã xoá vẫn
        // nhận mã và đặt lại mật khẩu thành công — người đã rời đi vẫn quay lại được. Vẫn trả
        // 200 âm thầm (không token, không mail) chứ không trả 403: trả lỗi ở đây cho kẻ xấu một
        // cách dò xem email nào đã bị khoá (prd/01 mục 7.1).
        // D3 — lọc thêm tài khoản Google thuần: gửi mã đặt lại cho tài khoản chưa từng có mật
        // khẩu là vô nghĩa. Vẫn trả 200 âm thầm chứ không báo lỗi, cùng lý do với hai cờ trên:
        // báo lỗi ở đây cho kẻ xấu một cách dò xem email nào dùng Google (prd/01 mục 9.4b).
        Optional<User> userOpt = userRepository
                .findByEmail(req.email().toLowerCase())
                .filter(u -> !u.isBlocked() && !u.isDeleted())
                .filter(u -> u.getPasswordHash() != null);
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
                .orElseThrow(() -> new BusinessException(ErrorCode.RESET_CODE_INVALID));

        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.RESET_CODE_INVALID);
        }

        // B3 — cửa thứ tư. Mã có thể đã phát hợp lệ TRƯỚC khi ADMIN khoá tài khoản, nên phải
        // kiểm lại tại thời điểm dùng chứ không chỉ tại thời điểm phát. Dùng chung
        // RESET_CODE_INVALID với mọi lý do khác để không lộ trạng thái tài khoản.
        User user = userRepository
                .findById(token.getUserId())
                .filter(u -> !u.isBlocked() && !u.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESET_CODE_INVALID));
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);

        token.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(token);

        // api/01 mục 9: "Thành công thì thu hồi toàn bộ thẻ của tài khoản" — không có ngoại lệ
        // "trừ phiên hiện tại" ở đây, khác với change-password.
        refreshTokenRepository.revokeAllActiveForUser(user.getId());
    }

    /**
     * C2: DELETE /auth/account — xoá MỀM, bốn bước trong cùng một transaction
     * (api/01-XAC-THUC.md mục 10).
     *
     * <p>Bốn bước phải nằm trọn trong một transaction: dừng giữa chừng sẽ để lại tài khoản đã
     * đánh dấu xoá nhưng refresh token còn sống 30 ngày — người vừa "xoá" vẫn xin được access
     * token mới qua {@code /auth/refresh} và tiếp tục dùng app như chưa có gì xảy ra.
     *
     * <p><b>Không viết lại logic chặn ở đây.</b> B3 đã lọc {@code isDeleted} ở cả bốn cửa vào
     * (login, refresh, forgot-password, reset-password), nên bật cờ là tài khoản tự động không
     * vào lại được. Email cũng không được giải phóng: {@code register} kiểm {@code existsByEmail}
     * không lọc {@code is_deleted} — cố ý, để người đăng ký mới không thừa hưởng lịch sử nhóm
     * của người trước.
     *
     * <p><b>Dữ liệu tài chính giữ nguyên</b> — không xoá ví, giao dịch, ngân sách, sổ nợ, mục
     * tiêu. Giao dịch ghi trên ví chung là dữ liệu của nhóm, thành viên còn lại vẫn cần thấy.
     */
    @Transactional
    public void deleteAccount(UUID userId, DeleteAccountRequest req) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy tài khoản."));

        // D3 — tài khoản Google thuần VẪN xoá được, chỉ bỏ qua bước so mật khẩu vì không có
        // mật khẩu nào để so. App thay ô nhập mật khẩu bằng ô gõ chữ XOA (prd/01 mục 9.4).
        // Không nới lỏng gì về bảo mật: endpoint này đã yêu cầu access token hợp lệ.
        if (user.getPasswordHash() != null
                && !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.WRONG_PASSWORD);
        }

        user.setDeleted(true);
        userRepository.save(user);

        refreshTokenRepository.revokeAllActiveForUser(userId);

        // Chưa có module group/ (Phase 5 backend chưa làm) nên chưa có repository cho
        // group_members — dùng native SQL lên bảng đã có sẵn từ V1. Rời nhóm chứ không xoá bản
        // ghi: lịch sử "ai từng ở trong nhóm" vẫn cần cho các giao dịch họ để lại trên ví chung.
        jdbcTemplate.update(
                "UPDATE group_members SET is_active = FALSE WHERE user_id = ? AND is_active", userId);
    }

    /**
     * D3: POST /auth/google (api/01-XAC-THUC.md muc 13).
     *
     * <p>Sau khi kiểm chứng chữ ký, tìm tài khoản theo ba nấc: google_id → email → tạo mới.
     * Cả ba nhánh trả về cùng một cấu trúc với đăng nhập thường, và đều trả 200 — người dùng
     * bấm một nút, họ không phân biệt "đăng ký" với "đăng nhập".
     *
     * <p><b>Vì sao email trùng thì tự liên kết (nấc 2):</b> Google đã xác thực họ là chủ hộp
     * thư ấy, nên chắc chắn cùng một người. Tạo tài khoản thứ hai sẽ khiến họ mất hết dữ liệu
     * cũ mà không hiểu vì sao.
     *
     * <p><b>Nấc 1 và 2 KHÔNG cập nhật username/avatarUrl theo token.</b> Hai trường này chỉ lấy
     * từ Google đúng một lần ở nấc 3. Sau đó chúng thuộc về người dùng — đồng bộ đè lên mỗi lần
     * đăng nhập sẽ xoá mất thay đổi họ vừa thực hiện ở màn Sửa hồ sơ (prd/01 mục 9.4b).
     */
    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest req) {
        GoogleIdTokenVerifier.GoogleUserInfo info = googleIdTokenVerifier.verify(req.idToken());
        String email = info.email().toLowerCase();
        Instant now = Instant.now();

        Optional<User> byGoogleId = userRepository.findByGoogleId(info.googleId());
        if (byGoogleId.isPresent()) {
            return finishGoogleLogin(byGoogleId.get(), now);
        }

        Optional<User> byEmail = userRepository.findByEmail(email);
        if (byEmail.isPresent()) {
            User user = byEmail.get();
            checkGoogleAccountState(user);

            user.setGoogleId(info.googleId());
            // Google vừa chứng minh họ là chủ hộp thư — đúng thứ cột này muốn biết. Giữ FALSE
            // là cố tình lưu một thông tin đã sai, và luồng xác thực email của ta thì prd/01
            // mục 11 ghi rõ là không làm đợt này, nên cờ sẽ kẹt FALSE mãi mãi.
            user.setConfirm(true);
            return finishGoogleLogin(user, now);
        }

        return createGoogleAccount(info, email, now);
    }

    /**
     * D3: chặn tài khoản khoá/xoá ở cửa thứ năm (api/01 mục 12).
     *
     * <p>Khác /auth/login ở một chỗ: ACCOUNT_BLOCKED trả ra NGAY, không đợi biết mật khẩu.
     * Google đã chứng minh người bấm nút là chủ tài khoản, nên không còn gì để giấu — nói thẳng
     * "bị khoá" hữu ích hơn là để họ thử lại vô ích.
     */
    private void checkGoogleAccountState(User user) {
        if (user.isDeleted()) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (user.isBlocked()) {
            throw new BusinessException(ErrorCode.ACCOUNT_BLOCKED);
        }
    }

    private AuthResponse finishGoogleLogin(User user, Instant now) {
        checkGoogleAccountState(user);
        user.setLastLoginAt(now);
        userRepository.save(user);
        return buildAuthResponse(user);
    }

    /**
     * D3: tạo tài khoản Google thuần + ví "Tiền mặt" trong cùng transaction, giống hệt
     * {@link #register} — thiếu ví thì người dùng vào app thấy màn hình trống và không ghi được
     * giao dịch nào.
     */
    private AuthResponse createGoogleAccount(
            GoogleIdTokenVerifier.GoogleUserInfo info, String email, Instant now) {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                // password_hash để NULL: chưa từng đặt mật khẩu. V13 đã gỡ NOT NULL khỏi cột này.
                .passwordHash(null)
                .username(resolveGoogleUsername(info.name(), email))
                .googleId(info.googleId())
                .plan("free")
                .role("USER")
                // Google đã xác thực hộp thư, bắt người dùng xác nhận lại là thừa.
                .isConfirm(true)
                .isBlocked(false)
                .isDeleted(false)
                .createdAt(now)
                .lastLoginAt(now)
                .build();
        userRepository.save(user);
        walletService.createDefaultCashWallet(user.getId(), now);

        return buildAuthResponse(user);
    }

    /**
     * Google không bắt buộc phải có trường {@code name} trong token (tài khoản ẩn tên, hoặc
     * scope bị từ chối). Cột {@code username} thì NOT NULL, nên phải có phương án dự phòng:
     * lấy phần trước dấu @ của email.
     */
    private String resolveGoogleUsername(String nameFromToken, String email) {
        if (nameFromToken != null && !nameFromToken.isBlank()) {
            return nameFromToken.trim();
        }
        return email.substring(0, email.indexOf('@'));
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

    /**
     * Gom một chỗ vì hai điểm gọi ({@code updateProfile} và {@code buildAuthResponse}) đều liệt
     * kê nguyên danh sách trường — thêm trường mới mà quên một chỗ thì hai điểm cuối trả khác
     * nhau, và app chỉ phát hiện ra khi bấm đúng luồng ít dùng hơn.
     */
    private UserSummaryResponse toSummary(User user) {
        return new UserSummaryResponse(
                user.getId(), user.getEmail(), user.getUsername(), user.getAvatarUrl(),
                user.getPlan(), user.isConfirm(), user.getRole(),
                user.getPasswordHash() != null, user.getGoogleId() != null,
                user.getCreatedAt());
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getPlan());
        String rawRefreshToken = issueRefreshToken(user.getId());

        UserSummaryResponse userDto = toSummary(user);

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

package com.datn.financeapp.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.auth.dto.request.EmailLoginRequest;
import com.datn.financeapp.auth.dto.request.ForgotPasswordRequest;
import com.datn.financeapp.auth.dto.request.GoogleLoginRequest;
import com.datn.financeapp.auth.dto.request.RefreshRequest;
import com.datn.financeapp.auth.dto.request.RegisterRequest;
import com.datn.financeapp.auth.dto.request.ResendVerificationRequest;
import com.datn.financeapp.auth.dto.request.ResetPasswordRequest;
import com.datn.financeapp.auth.dto.request.TokenCreateReq;
import com.datn.financeapp.auth.dto.request.VerifyEmailRequest;
import com.datn.financeapp.auth.entity.OtpModel;
import com.datn.financeapp.auth.enums.OtpType;
import com.datn.financeapp.auth.helper.ClientInfo;
import com.datn.financeapp.auth.helper.GoogleUserInfo;
import com.datn.financeapp.auth.repository.OtpRepository;
import com.datn.financeapp.common.mail.EmailService;
import com.datn.financeapp.performance.ServicePerfSupport;
import com.datn.financeapp.performance.ServicePerfSupport.Profile;
import com.datn.financeapp.performance.SqlCountingConfig;
import com.datn.financeapp.user.dto.request.UserGetReq;
import com.datn.financeapp.user.dto.response.UserRes;
import com.datn.financeapp.user.entity.Role;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.entity.UserRole;
import com.datn.financeapp.user.enums.RoleName;
import com.datn.financeapp.user.enums.UserStatus;
import com.datn.financeapp.user.repository.RoleRepository;
import com.datn.financeapp.user.repository.UserRepository;
import com.datn.financeapp.user.repository.UserRoleRepository;
import com.datn.financeapp.user.service.UserService;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Đo hiệu năng (số câu SQL + thời gian) của từng hàm trong module {@code auth}: {@link AuthService},
 * {@link TokenService} và {@link LoginService} (đăng nhập email và Google). Khung đo và cách đọc báo cáo xem
 * {@link ServicePerfSupport}.
 *
 * <p>Hồ sơ dữ liệu:
 * <ul>
 *   <li><b>Tốt nhất</b>: các hàm nhắm vào một người dùng thường (một vai trò, ít quyền) đang có
 *       {@value #FEW_TOKENS} refresh token.</li>
 *   <li><b>Tệ nhất</b>: nhắm vào chính admin (hai vai trò, đủ mọi quyền) đang có {@value #MANY_TOKENS}
 *       refresh token còn sống (nhiều thiết bị). Số quyền làm nặng các hàm nạp người dùng (đăng nhập, làm mới
 *       token), số token làm nặng các hàm thu hồi.</li>
 * </ul>
 * Hàm cần dữ liệu dùng một lần (đăng ký, xác thực, quên mật khẩu...) tự dựng người dùng và mã OTP mới cho từng lần gọi.
 *
 * <p>Gửi email và xác thực Google được thay bằng mock; {@link GoogleService#verifyIdToken} gọi máy chủ Google thật
 * nên không đo, chỉ ghi chú "bỏ qua".
 *
 * <p>Không phải test hành vi, chỉ ghi báo cáo vào log và {@code target/perf-auth.txt}. Chạy riêng:
 * {@code mvn test -Dtest=AuthServicePerfIntegrationTest}
 */
@Slf4j
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.context.annotation.Import({TestRedisConfig.class, SqlCountingConfig.class})
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
class AuthServicePerfIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@financeapp.com";
    private static final String ADMIN_PASSWORD = "Admin@123";
    private static final String KNOWN_PASSWORD = "matkhau123";
    private static final String OTP_CODE = "123456";
    private static final ClientInfo CLIENT = new ClientInfo("127.0.0.1", "perf-test");
    static final int FEW_TOKENS = 1;
    static final int MANY_TOKENS = 20;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1hdXRoLXBlcmYtdGVzdC0wNjA2MDYwNg==");
    }

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private GoogleService googleService;

    @Autowired
    private AuthService authService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private LoginService<EmailLoginRequest> emailLogin;

    @Autowired
    private LoginService<GoogleLoginRequest> googleLogin;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private OtpRepository otpRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ServicePerfSupport perf = new ServicePerfSupport("auth");
    private final AtomicInteger counter = new AtomicInteger();

    private String knownPasswordHash;
    private Role userRole;

    private record Subject(UUID id, String email, String password) {
    }

    @Test
    @DisplayName("Đo SQL và thời gian từng hàm của module auth ở hồ sơ tốt nhất và tệ nhất")
    void measureAuthModuleFunctions() throws Exception {
        knownPasswordHash = passwordEncoder.encode(KNOWN_PASSWORD);
        userRole = roleRepository.findByName(RoleName.ROLE_USER).orElseThrow();
        Subject plain = toSubject(createUser(UserStatus.ACTIVE), KNOWN_PASSWORD);
        Subject admin = toSubject(userRepository.findByEmail(ADMIN_EMAIL).orElseThrow(), ADMIN_PASSWORD);

        measureAll(Profile.TOT_NHAT, plain, FEW_TOKENS);
        measureAll(Profile.TE_NHAT, admin, MANY_TOKENS);

        perf.writeReport(
                "người dùng thường, " + FEW_TOKENS + " refresh token",
                "admin đủ quyền, " + MANY_TOKENS + " refresh token");
        assertThat(perf.measuredFunctionCount()).isPositive();
    }

    private void measureAll(Profile profile, Subject subject, int tokenCount) {
        AtomicReference<String> token = new AtomicReference<>();
        AtomicReference<User> target = new AtomicReference<>();
        AtomicReference<TokenCreateReq> tokenReq = new AtomicReference<>();

        measureAuthService(profile, subject, tokenCount, token, target);
        measureTokenService(profile, subject, tokenCount, token, tokenReq);
        measureLoginService(profile, subject);
    }

    private void measureAuthService(
            Profile profile, Subject subject, int tokenCount, AtomicReference<String> token, AtomicReference<User> target) {
        perf.measure(profile, "AuthService.register",
                () -> authService.register(new RegisterRequest(uniqueEmail(), KNOWN_PASSWORD)));
        perf.measure(profile, "AuthService.verifyEmail",
                () -> putOtp(OtpType.REGISTER_OTP, subject.email()),
                () -> authService.verifyEmail(new VerifyEmailRequest(subject.email(), OTP_CODE)));
        perf.measure(profile, "AuthService.resendVerification",
                () -> target.set(createUser(UserStatus.PENDING_VERIFY)),
                () -> authService.resendVerification(new ResendVerificationRequest(target.get().getEmail())));
        perf.measure(profile, "AuthService.refresh",
                () -> token.set(issueTokens(subject.id(), tokenCount)),
                () -> authService.refresh(new RefreshRequest(token.get())));
        perf.measure(profile, "AuthService.logout (một thiết bị)",
                () -> token.set(issueTokens(subject.id(), tokenCount)),
                () -> authService.logout(subject.id(), token.get(), false));
        perf.measure(profile, "AuthService.logout (mọi thiết bị)",
                () -> issueTokens(subject.id(), tokenCount),
                () -> authService.logout(subject.id(), null, true));
        perf.measure(profile, "AuthService.forgotPassword",
                () -> target.set(createUser(UserStatus.ACTIVE)),
                () -> authService.forgotPassword(new ForgotPasswordRequest(target.get().getEmail())));
        perf.measure(profile, "AuthService.resetPassword",
                () -> {
                    target.set(createUser(UserStatus.ACTIVE));
                    putOtp(OtpType.PASSWORD_RESET_OTP, target.get().getEmail());
                },
                () -> authService.resetPassword(
                        new ResetPasswordRequest(target.get().getEmail(), OTP_CODE, "matkhauMoi123")));
    }

    private void measureTokenService(
            Profile profile, Subject subject, int tokenCount,
            AtomicReference<String> token, AtomicReference<TokenCreateReq> tokenReq) {
        perf.measure(profile, "TokenService.create",
                () -> tokenReq.set(tokenRequestFor(subject.id())),
                () -> tokenService.create(tokenReq.get()));
        perf.measure(profile, "TokenService.checkRefreshAndGetUserId",
                () -> token.set(issueTokens(subject.id(), tokenCount)),
                () -> tokenService.checkRefreshAndGetUserId(token.get()));
        perf.measure(profile, "TokenService.revokeRefresh",
                () -> token.set(issueTokens(subject.id(), tokenCount)),
                () -> tokenService.revokeRefresh(token.get()));
        perf.measure(profile, "TokenService.revokeAllByUserId",
                () -> issueTokens(subject.id(), tokenCount),
                () -> tokenService.revokeAllByUserId(subject.id()));
        perf.measure(profile, "TokenService.revokeAllByEmail",
                () -> issueTokens(subject.id(), tokenCount),
                () -> tokenService.revokeAllByEmail(subject.email()));
    }

    private void measureLoginService(Profile profile, Subject subject) {
        perf.measure(profile, "LoginService<Email>.login",
                () -> emailLogin.login(new EmailLoginRequest(subject.email(), subject.password()), CLIENT));
        String googleId = "perf-google-" + subject.id();
        perf.measure(profile, "LoginService<Google>.login (xác thực Google đã mock)",
                () -> when(googleService.verifyIdToken(any()))
                        .thenReturn(new GoogleUserInfo(googleId, subject.email(), "Đo hiệu năng")),
                () -> googleLogin.login(new GoogleLoginRequest("id-token-gia"), CLIENT));
        perf.skip("GoogleService.verifyIdToken", "gọi máy chủ Google thật qua mạng");
    }

    // phát token cho người dùng, trả về token CUỐI; tokenCount > 1 để tái hiện người dùng đăng nhập nhiều thiết bị
    private String issueTokens(UUID userId, int tokenCount) {
        TokenCreateReq request = tokenRequestFor(userId);
        String last = null;
        for (int i = 0; i < tokenCount; i++)
            last = tokenService.create(request).refresh();
        return last;
    }

    // dựng yêu cầu phát token y như luồng đăng nhập thật: quyền và cấp bậc lấy từ chính người dùng
    private TokenCreateReq tokenRequestFor(UUID userId) {
        UserRes user = userService.get(new UserGetReq(userId));
        return new TokenCreateReq(user.id(), user.email(), user.plan().name(), user.getAuthorities(), user.getTopRoleLevel());
    }

    private void putOtp(OtpType type, String email) {
        otpRepository.save(OtpModel.builder()
                .type(type)
                .email(email)
                .code(OTP_CODE)
                .ttl(15L)
                .createdAt(Instant.now())
                .build());
    }

    private User createUser(UserStatus status) {
        User user = userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .email(uniqueEmail())
                .password(knownPasswordHash)
                .firstName("Đo")
                .lastName("Hiệu năng")
                .status(status)
                .createdAt(Instant.now())
                .build());
        UserRole link = new UserRole(user.getId(), userRole.getId());
        link.setUser(user);
        link.setRole(userRole);
        userRoleRepository.save(link);
        return user;
    }

    private Subject toSubject(User user, String password) {
        return new Subject(user.getId(), user.getEmail(), password);
    }

    private String uniqueEmail() {
        return "perf.auth." + counter.incrementAndGet() + "." + UUID.randomUUID() + "@example.com";
    }
}

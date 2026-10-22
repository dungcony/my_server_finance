package com.datn.financeapp.integrationTest.auth.service;

import com.datn.financeapp.module.auth.service.*;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.datn.financeapp.TestRedisConfig;
import com.datn.financeapp.module.auth.dto.request.VerifyEmailRequest;
import com.datn.financeapp.module.auth.dto.response.AuthRes;
import com.datn.financeapp.module.auth.helper.ClientInfo;
import com.datn.financeapp.module.auth.helper.GoogleUserInfo;
import com.datn.financeapp.module.auth.service.AuthService;
import com.datn.financeapp.module.auth.service.GoogleService;
import com.datn.financeapp.module.auth.service.impl.EmailLoginImpl;
import com.datn.financeapp.module.auth.service.impl.GoogleLoginImpl;
import com.datn.financeapp.module.user.dto.request.UpdatePassReq;
import com.datn.financeapp.module.auth.dto.request.ForgotPasswordRequest;
import com.datn.financeapp.module.auth.dto.request.GoogleLoginRequest;
import com.datn.financeapp.module.auth.dto.request.EmailLoginRequest;
import com.datn.financeapp.module.auth.dto.request.RegisterRequest;
import com.datn.financeapp.module.user.entity.User;
import com.datn.financeapp.module.auth.enums.OtpType;
import com.datn.financeapp.module.auth.repository.OtpRepository;
import com.datn.financeapp.module.auth.repository.LoginAttemptRepository;
import com.datn.financeapp.module.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.module.user.repository.UserRepository;
import com.datn.financeapp.module.user.service.UserBehavierService;
import com.datn.financeapp.common.mail.EmailService;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.module.wallet.entity.Wallet;
import com.datn.financeapp.module.wallet.repository.WalletRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Bước D3 của prd/01 — {@code POST /auth/google}: kiểm chứng {@code id_token}, tạo hoặc liên kết
 * tài khoản, và bốn chỗ phải xử lý {@code password_hash} rỗng sau V13.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(TestRedisConfig.class)
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
class AuthGoogleLoginIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1nb29nbGUtbG9naW4tdGVzdC0x");
    }

    private static final String PASSWORD = "matkhaudung1";
    private static final String ID_TOKEN = "khong-quan-trong-verifier-da-bi-mock";

    @Autowired
    private AuthService authService;

    @Autowired
    private EmailLoginImpl emailLoginService;

    @Autowired
    private GoogleLoginImpl googleLoginService;

    @Autowired
    private UserBehavierService userBehavierService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private OtpRepository otpRepository;

    @Autowired
    private LoginAttemptRepository loginAttemptRepository;

    @MockitoBean
    private GoogleService googleIdTokenVerifier;

    @MockitoSpyBean
    private EmailService emailService;

    @BeforeEach
    void cleanTables() {
        otpRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        loginAttemptRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    // -----------------------------------------------------------------
    // Nấc 3 — tạo tài khoản mới
    // -----------------------------------------------------------------

    @Test
    void loginWithGoogle_NewUser_CreatesConfirmedAccountWithDefaultWallet() {
        stubGoogle("sub-moi-001", "nguoi.moi@example.com", "Người Mới");

        AuthRes res = googleLoginService.login(new GoogleLoginRequest(ID_TOKEN), new ClientInfo("127.0.0.1", "junit"));

        assertThat(res.user().email()).isEqualTo("nguoi.moi@example.com");
        assertThat(res.token().access()).isNotBlank();
        assertThat(res.token().refresh()).isNotBlank();

        User saved = userRepository.findByEmail("nguoi.moi@example.com").orElseThrow();
        assertThat(saved.getGoogleId()).isEqualTo("sub-moi-001");
        assertThat(saved.getPassword()).isNull();
        assertThat(saved.isConfirm()).isTrue();

        // Trigger ở CSDL tạo đúng một ví "Tiền mặt" mặc định cho mọi tài khoản mới, kể cả đăng nhập Google
        List<Wallet> wallets = walletRepository.findAll().stream()
                .filter(w -> w.getUserId().equals(saved.getId()))
                .toList();
        assertThat(wallets).hasSize(1);
        assertThat(wallets.get(0).getName()).isEqualTo("Tiền mặt");
        assertThat(wallets.get(0).getIsDefault()).isTrue();
    }

    @Test
    void loginWithGoogle_TokenWithoutName_FallsBackToEmailLocalPart() {
        stubGoogle("sub-khong-ten", "an.danh@example.com", null);

        AuthRes res = googleLoginService.login(new GoogleLoginRequest(ID_TOKEN), new ClientInfo("127.0.0.1", "junit"));

        assertThat(res.user().email()).isEqualTo("an.danh@example.com");
    }

    // -----------------------------------------------------------------
    // Nấc 1 — đăng nhập lại bằng google_id
    // -----------------------------------------------------------------

    @Test
    void loginWithGoogle_SecondTime_ReusesSameAccount() {
        stubGoogle("sub-lap-lai", "lap.lai@example.com", "Lần Đầu");
        AuthRes first = googleLoginService.login(new GoogleLoginRequest(ID_TOKEN), new ClientInfo("127.0.0.1", "junit"));

        AuthRes second = googleLoginService.login(new GoogleLoginRequest(ID_TOKEN), new ClientInfo("127.0.0.1", "junit"));

        assertThat(second.user().id()).isEqualTo(first.user().id());
        assertThat(userRepository.findAll()).hasSize(1);
    }

    @Test
    void loginWithGoogle_NameChangedOnGoogleSide_DoesNotOverwriteLocalUsername() {
        stubGoogle("sub-doi-ten", "doi.ten@example.com", "Tên Ban Đầu");
        googleLoginService.login(new GoogleLoginRequest(ID_TOKEN), new ClientInfo("127.0.0.1", "junit"));

        stubGoogle("sub-doi-ten", "doi.ten@example.com", "Tên Mới Bên Google");
        AuthRes res = googleLoginService.login(new GoogleLoginRequest(ID_TOKEN), new ClientInfo("127.0.0.1", "junit"));

        assertThat(res.user().email()).isEqualTo("doi.ten@example.com");
    }

    // -----------------------------------------------------------------
    // Nấc 2 — email trùng thì tự liên kết
    // -----------------------------------------------------------------

    @Test
    void loginWithGoogle_EmailAlreadyRegistered_LinksToExistingAccount() {
        var registered = authService.register(
                new RegisterRequest("ca.hai@example.com", PASSWORD, "Tên Cũ"));

        stubGoogle("sub-lien-ket", "ca.hai@example.com", "Tên Bên Google");
        AuthRes viaGoogle = googleLoginService.login(new GoogleLoginRequest(ID_TOKEN), new ClientInfo("127.0.0.1", "junit"));

        assertThat(viaGoogle.user().id()).isEqualTo(registered.id());
        assertThat(userRepository.findAll()).hasSize(1);

        User linked = userRepository.findByEmail("ca.hai@example.com").orElseThrow();
        assertThat(linked.getGoogleId()).isEqualTo("sub-lien-ket");
        assertThat(linked.getPassword()).isNotNull();
        assertThat(linked.isConfirm()).isTrue();
    }

    @Test
    void loginWithGoogle_AfterLinking_PasswordLoginStillWorks() {
        authService.register(new RegisterRequest("van.dung.duoc@example.com", PASSWORD, "Người Dùng"));
        stubGoogle("sub-van-dung", "van.dung.duoc@example.com", "Người Dùng");
        googleLoginService.login(new GoogleLoginRequest(ID_TOKEN), new ClientInfo("127.0.0.1", "junit"));

        AuthRes viaPassword = emailLoginService.login(
                new EmailLoginRequest("van.dung.duoc@example.com", PASSWORD), new ClientInfo("127.0.0.1", "test"));

        assertThat(viaPassword.token().access()).isNotBlank();
    }

    @Test
    void loginWithGoogle_Linking_DoesNotOverwriteExistingUsername() {
        authService.register(new RegisterRequest("giu.ten@example.com", PASSWORD, "Tên Tôi Tự Đặt"));

        stubGoogle("sub-giu-ten", "giu.ten@example.com", "Tên Bên Google");
        AuthRes res = googleLoginService.login(new GoogleLoginRequest(ID_TOKEN), new ClientInfo("127.0.0.1", "junit"));

        assertThat(res.user().email()).isEqualTo("giu.ten@example.com");
    }

    // -----------------------------------------------------------------
    // Cửa thứ năm — tài khoản khoá/xoá (api/01 mục 12)
    // -----------------------------------------------------------------

    @Test
    void loginWithGoogle_BlockedAccount_ReturnsAccountBlocked() {
        authService.register(new RegisterRequest("bi.khoa@example.com", PASSWORD, "Bị Khoá"));
        setFlag("bi.khoa@example.com", u -> u.setBlocked(true));

        stubGoogle("sub-bi-khoa", "bi.khoa@example.com", "Bị Khoá");

        assertThatThrownBy(() -> googleLoginService.login(new GoogleLoginRequest(ID_TOKEN), new ClientInfo("127.0.0.1", "junit")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "AUTH_ACCOUNT_BLOCKED");
    }

    @Test
    void loginWithGoogle_DeletedAccount_ReturnsNotFound() {
        authService.register(new RegisterRequest("da.xoa@example.com", PASSWORD, "Đã Xoá"));
        setFlag("da.xoa@example.com", u -> u.setIsDeleted(true));

        stubGoogle("sub-da-xoa", "da.xoa@example.com", "Đã Xoá");

        assertThatThrownBy(() -> googleLoginService.login(new GoogleLoginRequest(ID_TOKEN), new ClientInfo("127.0.0.1", "junit")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "NOT_FOUND");
    }

    @Test
    void loginWithGoogle_BlockedAfterLinking_StillBlockedOnNextLogin() {
        stubGoogle("sub-khoa-sau", "khoa.sau@example.com", "Khoá Sau");
        googleLoginService.login(new GoogleLoginRequest(ID_TOKEN), new ClientInfo("127.0.0.1", "junit"));

        setFlag("khoa.sau@example.com", u -> u.setBlocked(true));

        assertThatThrownBy(() -> googleLoginService.login(new GoogleLoginRequest(ID_TOKEN), new ClientInfo("127.0.0.1", "junit")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "AUTH_ACCOUNT_BLOCKED");
    }

    // -----------------------------------------------------------------
    // Bốn chỗ phải xử lý password_hash rỗng sau V13
    // -----------------------------------------------------------------

    @Test
    void login_GoogleOnlyAccount_ReturnsInvalidCredentialsNotServerError() {
        stubGoogle("sub-khong-mk", "khong.mat.khau@example.com", "Không Mật Khẩu");
        googleLoginService.login(new GoogleLoginRequest(ID_TOKEN), new ClientInfo("127.0.0.1", "junit"));

        assertThatThrownBy(() -> emailLoginService.login(
                new EmailLoginRequest("khong.mat.khau@example.com", "thu.doan.mat.khau"),
                new ClientInfo("127.0.0.1", "test")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "AUTH_CREDENTIALS_INVALID");
    }

    @Test
    void changePassword_GoogleOnlyAccount_ReturnsNoPasswordSet() {
        stubGoogle("sub-doi-mk", "doi.mk@example.com", "Đổi Mật Khẩu");
        AuthRes res = googleLoginService.login(new GoogleLoginRequest(ID_TOKEN), new ClientInfo("127.0.0.1", "junit"));

        assertThatThrownBy(() -> userBehavierService.changePassword(
                res.user().id(), new UpdatePassReq("bat.ky", "matkhaumoi123")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "AUTH_PASSWORD_NOT_SET");
    }

    private void deleteMeAs(UUID userId, String password) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(userId.toString(), null, Collections.emptyList()));
        SecurityContextHolder.setContext(context);
        try {
            userBehavierService.deleteMe(password);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void deleteAccount_GoogleOnlyAccount_SucceedsWithoutPasswordCheck() {
        stubGoogle("sub-xoa-tk", "xoa.tk@example.com", "Xoá Tài Khoản");
        AuthRes res = googleLoginService.login(new GoogleLoginRequest(ID_TOKEN), new ClientInfo("127.0.0.1", "junit"));

        deleteMeAs(res.user().id(), "XOA");

        User deleted = userRepository.findById(res.user().id()).orElseThrow();
        assertThat(deleted.getIsDeleted()).isTrue();
    }

    @Test
    void forgotPassword_GoogleOnlyAccount_SendsCodeSoTheyCanSetAPassword() {
        stubGoogle("sub-quen-mk", "quen.mk@example.com", "Quên Mật Khẩu");
        googleLoginService.login(new GoogleLoginRequest(ID_TOKEN), new ClientInfo("127.0.0.1", "junit"));
        Mockito.reset(emailService);

        authService.forgotPassword(new ForgotPasswordRequest("quen.mk@example.com"));

        Mockito.verify(emailService).sendPasswordResetCode(Mockito.eq("quen.mk@example.com"), anyString());
        assertThat(otpRepository.findByTypeAndEmail(OtpType.PASSWORD_RESET_OTP, "quen.mk@example.com")).isPresent();
    }

    @Test
    void deleteAccount_LinkedAccount_StillChecksPassword() {
        authService.register(new RegisterRequest("van.kiem@example.com", PASSWORD, "Vẫn Kiểm"));
        stubGoogle("sub-van-kiem", "van.kiem@example.com", "Vẫn Kiểm");
        AuthRes res = googleLoginService.login(new GoogleLoginRequest(ID_TOKEN), new ClientInfo("127.0.0.1", "junit"));

        assertThatThrownBy(() -> deleteMeAs(res.user().id(), "mat.khau.sai"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "AUTH_PASSWORD_INCORRECT");
    }

    // -----------------------------------------------------------------
    // D4 — hai trường app dùng để ẩn nút Đổi mật khẩu
    // -----------------------------------------------------------------

    @Test
    void loginWithGoogle_GoogleOnlyAccount_ReportsNoPasswordAndLinked() {
        stubGoogle("sub-co-truong", "co.truong@example.com", "Có Trường");

        AuthRes res = googleLoginService.login(new GoogleLoginRequest(ID_TOKEN), new ClientInfo("127.0.0.1", "junit"));

        assertThat(res.user().hasPassword()).isFalse();
        assertThat(res.user().googleLinked()).isTrue();
    }

    @Test
    void login_EmailOnlyAccount_ReportsHasPasswordAndNotLinked() {
        registerAndVerify("chi.email@example.com", "Chỉ Email");

        AuthRes res = emailLoginService.login(
                new EmailLoginRequest("chi.email@example.com", PASSWORD), new ClientInfo("127.0.0.1", "test"));

        assertThat(res.user().hasPassword()).isTrue();
        assertThat(res.user().googleLinked()).isFalse();
    }

    @Test
    void loginWithGoogle_LinkedAccount_ReportsBothTrue() {
        authService.register(new RegisterRequest("ca.hai.co@example.com", PASSWORD, "Cả Hai"));
        stubGoogle("sub-ca-hai", "ca.hai.co@example.com", "Cả Hai");

        AuthRes res = googleLoginService.login(new GoogleLoginRequest(ID_TOKEN), new ClientInfo("127.0.0.1", "junit"));

        assertThat(res.user().hasPassword()).isTrue();
        assertThat(res.user().googleLinked()).isTrue();
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private void registerAndVerify(String email, String username) {
        authService.register(new RegisterRequest(email, PASSWORD, username));
        String code = otpRepository
                .findByTypeAndEmail(OtpType.REGISTER_OTP, email)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy mã OTP đăng ký cho " + email))
                .getCode();
        authService.verifyEmail(new VerifyEmailRequest(email, code));
    }

    private void stubGoogle(String sub, String email, String name) {
        when(googleIdTokenVerifier.verifyIdToken(anyString()))
                .thenReturn(new GoogleUserInfo(sub, email, name));
    }

    private void setFlag(String email, Consumer<User> mutator) {
        User user = userRepository.findByEmail(email).orElseThrow();
        mutator.accept(user);
        userRepository.save(user);
    }
}

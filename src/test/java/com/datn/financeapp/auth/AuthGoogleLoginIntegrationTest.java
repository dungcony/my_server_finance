package com.datn.financeapp.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.datn.financeapp.auth.dto.response.AuthResponse;
import com.datn.financeapp.auth.dto.request.ChangePasswordRequest;
import com.datn.financeapp.auth.dto.request.DeleteAccountRequest;
import com.datn.financeapp.auth.dto.request.ForgotPasswordRequest;
import com.datn.financeapp.auth.dto.request.GoogleLoginRequest;
import com.datn.financeapp.auth.dto.request.LoginRequest;
import com.datn.financeapp.auth.dto.request.RegisterRequest;
import com.datn.financeapp.auth.entity.User;
import com.datn.financeapp.auth.repository.LoginAttemptRepository;
import com.datn.financeapp.auth.repository.PasswordResetTokenRepository;
import com.datn.financeapp.auth.repository.RefreshTokenRepository;
import com.datn.financeapp.auth.repository.UserRepository;
import com.datn.financeapp.auth.service.AuthService;
import com.datn.financeapp.auth.service.GoogleIdTokenVerifier;
import com.datn.financeapp.auth.service.PasswordResetNotifier;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.wallet.entity.Wallet;
import com.datn.financeapp.wallet.repository.WalletRepository;
import java.util.List;
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
 *
 * <p>{@link GoogleIdTokenVerifier} bị {@code @MockitoBean} đè hoàn toàn — bản thật gọi ra máy chủ
 * Google lấy khoá công khai, mà test thì không có cách nào sinh ra một {@code id_token} hợp lệ
 * để thử. Thứ đáng kiểm ở đây là các bước nghiệp vụ SAU khi chữ ký đã hợp lệ.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
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
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private LoginAttemptRepository loginAttemptRepository;

    @MockitoBean
    private GoogleIdTokenVerifier googleIdTokenVerifier;

    @MockitoSpyBean
    private PasswordResetNotifier passwordResetNotifier;

    @BeforeEach
    void cleanTables() {
        passwordResetTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        loginAttemptRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    // -----------------------------------------------------------------
    // Nấc 3 — tạo tài khoản mới
    // -----------------------------------------------------------------

    @Test
    void loginWithGoogle_NewUser_CreatesAccountWithCashWalletAndConfirmedFlag() {
        stubGoogle("sub-moi-001", "nguoi.moi@example.com", "Người Mới");

        AuthResponse res = authService.loginWithGoogle(new GoogleLoginRequest(ID_TOKEN));

        assertThat(res.user().email()).isEqualTo("nguoi.moi@example.com");
        assertThat(res.user().username()).isEqualTo("Người Mới");
        assertThat(res.accessToken()).isNotBlank();
        assertThat(res.refreshToken()).isNotBlank();

        User saved = userRepository.findByEmail("nguoi.moi@example.com").orElseThrow();
        assertThat(saved.getGoogleId()).isEqualTo("sub-moi-001");
        assertThat(saved.getPasswordHash()).isNull();
        assertThat(saved.isConfirm()).isTrue();
        assertThat(saved.getRole()).isEqualTo("USER");

        List<Wallet> wallets = walletRepository.findAll().stream()
                .filter(w -> w.getUserId().equals(saved.getId()))
                .toList();
        assertThat(wallets).hasSize(1);
        assertThat(wallets.get(0).getName()).isEqualTo("Tiền mặt");
    }

    @Test
    void loginWithGoogle_TokenWithoutName_FallsBackToEmailLocalPart() {
        stubGoogle("sub-khong-ten", "an.danh@example.com", null);

        AuthResponse res = authService.loginWithGoogle(new GoogleLoginRequest(ID_TOKEN));

        // username là NOT NULL ở CSDL, nên phải có phương án dự phòng khi Google không trả `name`
        assertThat(res.user().username()).isEqualTo("an.danh");
    }

    // -----------------------------------------------------------------
    // Nấc 1 — đăng nhập lại bằng google_id
    // -----------------------------------------------------------------

    @Test
    void loginWithGoogle_SecondTime_ReusesSameAccount() {
        stubGoogle("sub-lap-lai", "lap.lai@example.com", "Lần Đầu");
        AuthResponse first = authService.loginWithGoogle(new GoogleLoginRequest(ID_TOKEN));

        AuthResponse second = authService.loginWithGoogle(new GoogleLoginRequest(ID_TOKEN));

        assertThat(second.user().id()).isEqualTo(first.user().id());
        assertThat(userRepository.findAll()).hasSize(1);
    }

    @Test
    void loginWithGoogle_NameChangedOnGoogleSide_DoesNotOverwriteLocalUsername() {
        stubGoogle("sub-doi-ten", "doi.ten@example.com", "Tên Ban Đầu");
        authService.loginWithGoogle(new GoogleLoginRequest(ID_TOKEN));

        stubGoogle("sub-doi-ten", "doi.ten@example.com", "Tên Mới Bên Google");
        AuthResponse res = authService.loginWithGoogle(new GoogleLoginRequest(ID_TOKEN));

        // prd/01 mục 9.4b: chỉ lấy tên từ Google đúng một lần lúc tạo. Đồng bộ đè lên mỗi lần
        // đăng nhập sẽ xoá mất thay đổi người dùng vừa thực hiện, trông hệt lỗi mất dữ liệu.
        assertThat(res.user().username()).isEqualTo("Tên Ban Đầu");
    }

    @Test
    void loginWithGoogle_EmailChangedOnGoogleSide_StillFindsAccountBySub() {
        stubGoogle("sub-doi-mail", "cu@example.com", "Người Dùng");
        AuthResponse first = authService.loginWithGoogle(new GoogleLoginRequest(ID_TOKEN));

        // Người dùng đổi email trong tài khoản Google. `sub` không đổi nên vẫn phải vào đúng
        // tài khoản cũ — đây là lý do lưu google_id thay vì dựa vào email.
        stubGoogle("sub-doi-mail", "moi@example.com", "Người Dùng");
        AuthResponse second = authService.loginWithGoogle(new GoogleLoginRequest(ID_TOKEN));

        assertThat(second.user().id()).isEqualTo(first.user().id());
        assertThat(userRepository.findAll()).hasSize(1);
    }

    // -----------------------------------------------------------------
    // Nấc 2 — email trùng thì tự liên kết
    // -----------------------------------------------------------------

    @Test
    void loginWithGoogle_EmailAlreadyRegistered_LinksToExistingAccount() {
        AuthResponse registered = authService.register(
                new RegisterRequest("ca.hai@example.com", PASSWORD, "Tên Cũ"));

        stubGoogle("sub-lien-ket", "ca.hai@example.com", "Tên Bên Google");
        AuthResponse viaGoogle = authService.loginWithGoogle(new GoogleLoginRequest(ID_TOKEN));

        assertThat(viaGoogle.user().id()).isEqualTo(registered.user().id());
        assertThat(userRepository.findAll()).hasSize(1);

        User linked = userRepository.findByEmail("ca.hai@example.com").orElseThrow();
        assertThat(linked.getGoogleId()).isEqualTo("sub-lien-ket");
        // Liên kết chứ không thay thế: mật khẩu cũ còn nguyên
        assertThat(linked.getPasswordHash()).isNotNull();
        // Google vừa chứng minh họ là chủ hộp thư (prd/01 mục 9.4b)
        assertThat(linked.isConfirm()).isTrue();
    }

    @Test
    void loginWithGoogle_AfterLinking_PasswordLoginStillWorks() {
        authService.register(new RegisterRequest("van.dung.duoc@example.com", PASSWORD, "Người Dùng"));
        stubGoogle("sub-van-dung", "van.dung.duoc@example.com", "Người Dùng");
        authService.loginWithGoogle(new GoogleLoginRequest(ID_TOKEN));

        AuthResponse viaPassword = authService.login(
                new LoginRequest("van.dung.duoc@example.com", PASSWORD), "127.0.0.1", "test");

        assertThat(viaPassword.accessToken()).isNotBlank();
    }

    @Test
    void loginWithGoogle_Linking_DoesNotOverwriteExistingUsername() {
        authService.register(new RegisterRequest("giu.ten@example.com", PASSWORD, "Tên Tôi Tự Đặt"));

        stubGoogle("sub-giu-ten", "giu.ten@example.com", "Tên Bên Google");
        AuthResponse res = authService.loginWithGoogle(new GoogleLoginRequest(ID_TOKEN));

        assertThat(res.user().username()).isEqualTo("Tên Tôi Tự Đặt");
    }

    // -----------------------------------------------------------------
    // Cửa thứ năm — tài khoản khoá/xoá (api/01 mục 12)
    // -----------------------------------------------------------------

    @Test
    void loginWithGoogle_BlockedAccount_ReturnsAccountBlocked() {
        authService.register(new RegisterRequest("bi.khoa@example.com", PASSWORD, "Bị Khoá"));
        setFlag("bi.khoa@example.com", u -> u.setBlocked(true));

        stubGoogle("sub-bi-khoa", "bi.khoa@example.com", "Bị Khoá");

        // Khác /auth/login: mã này trả ra NGAY, không đợi biết mật khẩu. Google đã chứng minh
        // người bấm nút là chủ tài khoản nên không còn gì để giấu.
        assertThatThrownBy(() -> authService.loginWithGoogle(new GoogleLoginRequest(ID_TOKEN)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ACCOUNT_BLOCKED");
    }

    @Test
    void loginWithGoogle_DeletedAccount_ReturnsInvalidCredentials() {
        authService.register(new RegisterRequest("da.xoa@example.com", PASSWORD, "Đã Xoá"));
        setFlag("da.xoa@example.com", u -> u.setDeleted(true));

        stubGoogle("sub-da-xoa", "da.xoa@example.com", "Đã Xoá");

        // Tài khoản đã xoá coi như không tồn tại — nói "đã xoá" là xác nhận email từng đăng ký
        assertThatThrownBy(() -> authService.loginWithGoogle(new GoogleLoginRequest(ID_TOKEN)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_CREDENTIALS");
    }

    @Test
    void loginWithGoogle_BlockedAfterLinking_StillBlockedOnNextLogin() {
        stubGoogle("sub-khoa-sau", "khoa.sau@example.com", "Khoá Sau");
        authService.loginWithGoogle(new GoogleLoginRequest(ID_TOKEN));

        setFlag("khoa.sau@example.com", u -> u.setBlocked(true));

        // Nấc 1 (tìm theo google_id) cũng phải kiểm cờ, không chỉ nấc 2
        assertThatThrownBy(() -> authService.loginWithGoogle(new GoogleLoginRequest(ID_TOKEN)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ACCOUNT_BLOCKED");
    }

    // -----------------------------------------------------------------
    // Bốn chỗ phải xử lý password_hash rỗng sau V13
    // -----------------------------------------------------------------

    @Test
    void login_GoogleOnlyAccount_ReturnsInvalidCredentialsNotServerError() {
        stubGoogle("sub-khong-mk", "khong.mat.khau@example.com", "Không Mật Khẩu");
        authService.loginWithGoogle(new GoogleLoginRequest(ID_TOKEN));

        // Trước khi vá, passwordEncoder.matches(x, null) ném NullPointerException -> 500
        assertThatThrownBy(() -> authService.login(
                        new LoginRequest("khong.mat.khau@example.com", "thu.doan.mat.khau"),
                        "127.0.0.1", "test"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_CREDENTIALS");
    }

    @Test
    void changePassword_GoogleOnlyAccount_ReturnsNoPasswordSet() {
        stubGoogle("sub-doi-mk", "doi.mk@example.com", "Đổi Mật Khẩu");
        AuthResponse res = authService.loginWithGoogle(new GoogleLoginRequest(ID_TOKEN));

        // App đã ẩn nút Đổi mật khẩu, nhưng backend vẫn phải tự chặn: bảo vệ chỉ ở phía client
        // thì ai gọi thẳng API cũng qua
        assertThatThrownBy(() -> authService.changePassword(
                        res.user().id(), new ChangePasswordRequest("bat.ky", "matkhaumoi123")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "NO_PASSWORD_SET");
    }

    @Test
    void deleteAccount_GoogleOnlyAccount_SucceedsWithoutPasswordCheck() {
        stubGoogle("sub-xoa-tk", "xoa.tk@example.com", "Xoá Tài Khoản");
        AuthResponse res = authService.loginWithGoogle(new GoogleLoginRequest(ID_TOKEN));

        // Không có mật khẩu nào để so, nhưng vẫn phải xoá được (prd/01 mục 9.4 — app gửi chữ XOA)
        authService.deleteAccount(res.user().id(), new DeleteAccountRequest("XOA"));

        User deleted = userRepository.findById(res.user().id()).orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();
    }

    @Test
    void forgotPassword_GoogleOnlyAccount_ReturnsSilentlyWithoutSendingCode() {
        stubGoogle("sub-quen-mk", "quen.mk@example.com", "Quên Mật Khẩu");
        authService.loginWithGoogle(new GoogleLoginRequest(ID_TOKEN));
        Mockito.reset(passwordResetNotifier);

        authService.forgotPassword(new ForgotPasswordRequest("quen.mk@example.com"));

        // Trả 200 âm thầm chứ không báo lỗi: báo lỗi cho kẻ xấu một cách dò xem email nào dùng
        // Google. Nhưng cũng không gửi mã — tài khoản chưa từng có mật khẩu để đặt lại.
        Mockito.verify(passwordResetNotifier, Mockito.never()).sendResetCode(anyString(), anyString());
        assertThat(passwordResetTokenRepository.findAll()).isEmpty();
    }

    @Test
    void deleteAccount_LinkedAccount_StillChecksPassword() {
        authService.register(new RegisterRequest("van.kiem@example.com", PASSWORD, "Vẫn Kiểm"));
        stubGoogle("sub-van-kiem", "van.kiem@example.com", "Vẫn Kiểm");
        AuthResponse res = authService.loginWithGoogle(new GoogleLoginRequest(ID_TOKEN));

        // Tài khoản ĐÃ LIÊN KẾT vẫn có mật khẩu, nên không được nới lỏng bước so mật khẩu
        assertThatThrownBy(() -> authService.deleteAccount(
                        res.user().id(), new DeleteAccountRequest("mat.khau.sai")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "WRONG_PASSWORD");
    }

    // -----------------------------------------------------------------
    // D4 — hai trường app dùng để ẩn nút Đổi mật khẩu
    // -----------------------------------------------------------------

    @Test
    void loginWithGoogle_GoogleOnlyAccount_ReportsNoPasswordAndLinked() {
        stubGoogle("sub-co-truong", "co.truong@example.com", "Có Trường");

        AuthResponse res = authService.loginWithGoogle(new GoogleLoginRequest(ID_TOKEN));

        assertThat(res.user().hasPassword()).isFalse();
        assertThat(res.user().googleLinked()).isTrue();
    }

    @Test
    void login_EmailOnlyAccount_ReportsHasPasswordAndNotLinked() {
        authService.register(new RegisterRequest("chi.email@example.com", PASSWORD, "Chỉ Email"));

        AuthResponse res = authService.login(
                new LoginRequest("chi.email@example.com", PASSWORD), "127.0.0.1", "test");

        assertThat(res.user().hasPassword()).isTrue();
        assertThat(res.user().googleLinked()).isFalse();
    }

    @Test
    void loginWithGoogle_LinkedAccount_ReportsBothTrue() {
        authService.register(new RegisterRequest("ca.hai.co@example.com", PASSWORD, "Cả Hai"));
        stubGoogle("sub-ca-hai", "ca.hai.co@example.com", "Cả Hai");

        AuthResponse res = authService.loginWithGoogle(new GoogleLoginRequest(ID_TOKEN));

        // Tài khoản đã liên kết VẪN đổi mật khẩu được — đây là lý do không dùng is_confirm
        // thay cho has_password: cờ đó cũng true ở đây mà lại nói về chuyện khác.
        assertThat(res.user().hasPassword()).isTrue();
        assertThat(res.user().googleLinked()).isTrue();
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private void stubGoogle(String sub, String email, String name) {
        when(googleIdTokenVerifier.verify(anyString()))
                .thenReturn(new GoogleIdTokenVerifier.GoogleUserInfo(sub, email, name));
    }

    private void setFlag(String email, Consumer<User> mutator) {
        User user = userRepository.findByEmail(email).orElseThrow();
        mutator.accept(user);
        userRepository.save(user);
    }
}

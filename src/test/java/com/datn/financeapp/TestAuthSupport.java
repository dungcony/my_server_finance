package com.datn.financeapp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datn.financeapp.auth.entity.OtpModel;
import com.datn.financeapp.auth.enums.OtpType;
import com.datn.financeapp.auth.repository.OtpRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Lấy access token cho test tích hợp — một chỗ duy nhất biết luồng đăng ký hiện tại.
 *
 * <p><b>Vì sao cần.</b> Trước 13/09/2026 {@code POST /auth/register} trả thẳng access token, nên
 * mọi test chỉ cần đọc {@code data.access_token}. Từ khi thêm luồng xác thực email (PR #3),
 * register chỉ tạo tài khoản {@code PENDING_VERIFY} và gửi OTP — trường {@code access_token}
 * biến mất khỏi response. 33 file test vẫn đọc trường đó, nhận {@code null}, gửi header
 * {@code Bearer null} và nhận 401; 175/264 test đỏ vì đúng một lý do này.
 *
 * <p>Gom vào một lớp để lần sau luồng đăng nhập đổi nữa thì sửa một chỗ, không phải đi sửa 33 file.
 *
 * <p><b>Đi đúng luồng thật</b> (register → đọc OTP → verify-email) thay vì kích hoạt tài khoản
 * bằng UPDATE thẳng vào CSDL: test tích hợp mà lách qua chính API nó đang kiểm thì mất luôn khả
 * năng phát hiện luồng xác thực hỏng.
 *
 * <p><b>KHÔNG gắn {@code @Component} vào lớp này.</b> Gắn vào thì component-scan nạp nó vào MỌI
 * context test, kể cả test không có {@code @AutoConfigureMockMvc} (ví dụ
 * {@code WalletTransferConcurrencyTest}) — thiếu bean {@link MockMvc}, context hỏng ngay lúc
 * khởi tạo và toàn bộ test của lớp đó chết theo. Nạp tường minh bằng
 * {@code @Import({TestRedisConfig.class, TestAuthSupport.class})} ở đúng những lớp cần.
 */
public class TestAuthSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OtpRepository otpRepository;

    /** Đăng ký tài khoản mới, xác thực email, trả access token dùng ngay được. */
    public String registerAndGetAccessToken(String email) throws Exception {
        return registerAndGetAccessToken(email, "Người Kiểm Thử");
    }

    public String registerAndGetAccessToken(String email, String displayName) throws Exception {
        register(email, displayName);
        return verifyEmailAndGetAccessToken(email);
    }

    /** Chỉ đăng ký, chưa xác thực — cho test cần tài khoản ở trạng thái {@code PENDING_VERIFY}. */
    public void register(String email, String displayName) throws Exception {
        Map<String, Object> body = Map.of(
                "email", email,
                "password", "matkhau123",
                "username", displayName);
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    /**
     * Đọc mã OTP mà register vừa lưu vào Redis rồi gọi {@code /auth/verify-email}.
     *
     * <p>Đọc thẳng từ {@link OtpRepository} vì mã chỉ được gửi qua email — test không có hộp thư.
     * Đây là chỗ duy nhất được phép nhìn vào OTP; phần còn lại của test đi qua API như người dùng.
     */
    public String verifyEmailAndGetAccessToken(String email) throws Exception {
        OtpModel otp = otpRepository
                .findByTypeAndEmail(OtpType.REGISTER_OTP, email)
                .orElseThrow(() -> new IllegalStateException(
                        "Không tìm thấy mã OTP đăng ký cho " + email
                                + ". Redis có đang chạy không? Test phải @Import(TestRedisConfig.class)."));

        Map<String, Object> body = Map.of("email", email, "code", otp.getCode());
        String response = mockMvc.perform(post("/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<?, ?> parsed = objectMapper.readValue(response, Map.class);
        Map<?, ?> data = (Map<?, ?>) parsed.get("data");
        return (String) data.get("access_token");
    }

    /**
     * Ví đầu tiên của tài khoản, tạo mới nếu chưa có.
     *
     * <p>Trước 13/09/2026 máy chủ tạo sẵn một ví "Tiền mặt" khi đăng ký, nên test chỉ cần lấy
     * phần tử đầu của {@code GET /wallets}. Điều đó đã bỏ (api/01 mục 1): tài khoản mới có danh
     * sách ví RỖNG, và {@code wallets.get(0)} ném {@code IndexOutOfBoundsException}. Helper này
     * tạo ví khi cần để test nào chỉ mượn một ví làm bối cảnh không phải quan tâm chuyện đó.
     */
    public String firstWalletId(String token) throws Exception {
        String response = mockMvc.perform(get("/wallets").header("Authorization", "Bearer " + token))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Map<?, ?> parsed = objectMapper.readValue(response, Map.class);
        java.util.List<?> wallets = (java.util.List<?>) parsed.get("data");

        if (wallets != null && !wallets.isEmpty()) {
            return (String) ((Map<?, ?>) wallets.get(0)).get("id");
        }
        return createWallet(token, "Ví Kiểm Thử", "cash", 0);
    }

    /** Tạo ví và trả id. */
    public String createWallet(String token, String name, String type, long initialBalance)
            throws Exception {
        Map<String, Object> body = Map.of(
                "name", name,
                "type", type,
                "initial_balance", initialBalance);
        String response = mockMvc.perform(post("/wallets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Map<?, ?> parsed = objectMapper.readValue(response, Map.class);
        Map<?, ?> data = (Map<?, ?>) parsed.get("data");
        return (String) data.get("id");
    }

    /** Đăng nhập tài khoản đã xác thực từ trước. */
    public String loginAndGetAccessToken(String email, String password) throws Exception {
        Map<String, Object> body = Map.of("email", email, "password", password);
        String response = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<?, ?> parsed = objectMapper.readValue(response, Map.class);
        Map<?, ?> data = (Map<?, ?>) parsed.get("data");
        return (String) data.get("access_token");
    }
}

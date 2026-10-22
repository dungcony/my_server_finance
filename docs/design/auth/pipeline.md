# LUỒNG XỬ LÝ & BẢO MẬT — PHÂN HỆ XÁC THỰC (AUTH)

> **File này trả lời:** Hệ thống xử lý các quy trình đăng ký, đăng nhập, xoay vòng token, xác thực OTP và bảo mật theo trình tự nào.  
> Cấu trúc bảng & Entity → [lop-thuc-the.md](lop-thuc-the.md) · Endpoint API → [api.md](api.md) · Luật bảo mật → [rule.md](rule.md)

---

## 📑 MỤC LỤC

- [1. Luồng Đăng ký & Kích hoạt Email qua OTP](#1-luong-dang-ky-otp)
- [2. Luồng Đăng nhập Email/Password & Chống Brute-force](#2-luong-dang-nhap)
- [3. Luồng Đăng nhập qua Google OAuth2](#3-luong-google)
- [4. Cơ chế Xoay vòng Refresh Token & Phát hiện Tái sử dụng (Token Rotation & Reuse Detection)](#4-co-che-token-rotation)
- [5. Luồng Quên & Đặt lại mật khẩu](#5-luong-quen-mat-khau)
- [6. Luồng Đăng xuất phiên làm việc](#6-luong-dang-xuat)

---

## 1. Luồng Đăng ký & Kích hoạt Email qua OTP <a id="1-luong-dang-ky-otp"></a>

```mermaid
sequenceDiagram
    autonumber
    actor Client as Client / User
    participant AuthCtrl as AuthController
    participant AuthSvc as AuthServiceImpl
    participant UserSvc as UserService
    participant Redis as Redis (OtpModel)
    participant Mail as EmailService

    Client->>AuthCtrl: POST /auth/register {email, password}
    AuthCtrl->>AuthSvc: register(req)
    AuthSvc->>UserSvc: existByEmail(email)
    alt Email đã tồn tại
        AuthSvc-->>Client: 409 AUTH_EMAIL_ALREADY_EXISTS
    else Email hợp lệ
        AuthSvc->>UserSvc: create(user, status=PENDING, passwordHash)
        AuthSvc->>Redis: save(OtpModel: code=6 digits, ttl=15m)
        AuthSvc->>Mail: sendVerificationOtp(email, code)
        AuthSvc-->>Client: 201 Created {id, email}
    end

    Note over Client,Mail: Người dùng kiểm tra email và nhập mã OTP

    Client->>AuthCtrl: POST /auth/verify-email {email, code}
    AuthCtrl->>AuthSvc: verifyEmail(req)
    AuthSvc->>Redis: findByTypeAndEmail(REGISTER_OTP, email)
    alt Mã không khớp
        AuthSvc->>Redis: attempts++ (Nếu >= 5 thì xóa OTP)
        AuthSvc-->>Client: 400 AUTH_CODE_INVALID
    else Mã chính xác
        AuthSvc->>Redis: delete(REGISTER_OTP, email)
        AuthSvc->>UserSvc: updateStatus(email, ACTIVE)
        AuthSvc->>AuthSvc: tokenService.create(...)
        AuthSvc-->>Client: 200 OK {user, tokens: {accessToken, refreshToken}}
    end
```

**Quy trình chi tiết:**
1. **Đăng ký:**
   - Hệ thống chuẩn hóa email về chữ thường và kiểm tra trùng lặp trong CSDL.
   - Tạo bản ghi người dùng với mật khẩu mã hóa BCrypt và trạng thái `status = PENDING`.
   - Sinh chuỗi ngẫu nhiên 6 chữ số, lưu vào Redis (`otp:register_otp:{email}`) với TTL = 15 phút và gửi email thông báo.
2. **Xác thực:**
   - Tra cứu bản ghi OTP từ Redis.
   - Nếu mã không đúng: tăng `attempts`. Nếu `attempts >= 5`, xóa ngay bản ghi OTP khỏi Redis để ngăn chặn tấn công brute-force.
   - Nếu mã đúng: xóa OTP, chuyển trạng thái người dùng thành `ACTIVE`, đồng thời cấp phát cặp Access Token và Refresh Token ngay lập tức.
3. **Gửi lại mã (`resend-verification`):**
   - Kiểm tra giãn cách thời gian tối thiểu 60 giây (`cooldown`) so với thời điểm tạo mã OTP trước đó để tránh spam mail server.

---

## 2. Luồng Đăng nhập Email/Password & Chống Brute-force <a id="2-luong-dang-nhap"></a>

```mermaid
sequenceDiagram
    autonumber
    actor Client as Client / User
    participant LoginCtrl as LoginController
    participant EmailLogin as EmailLoginImpl
    participant UserSvc as UserService
    participant Repo as LoginAttemptRepository
    participant TokenSvc as TokenServiceImpl

    Client->>LoginCtrl: POST /auth/login {email, password}
    LoginCtrl->>EmailLogin: login(req, clientInfo)
    EmailLogin->>Repo: isLockedOut(email) - Kiểm tra 5 lần gần nhất
    alt Bị khóa do sai quá 5 lần
        EmailLogin-->>Client: 429 AUTH_ACCOUNT_LOCKED
    end

    EmailLogin->>UserSvc: get(email)
    Note over EmailLogin: So khớp BCrypt password
    EmailLogin->>Repo: save(LoginAttempt: succeeded, ip, userAgent)

    alt Sai email hoặc mật khẩu
        EmailLogin-->>Client: 400 AUTH_CREDENTIALS_INVALID
    else Đúng mật khẩu nhưng bị Admin khóa
        EmailLogin-->>Client: 403 AUTH_ACCOUNT_BLOCKED
    else Chưa kích hoạt email
        EmailLogin-->>Client: 403 AUTH_ACCOUNT_NOT_VERIFIED
    else Thành công
        EmailLogin->>TokenSvc: create(userId, email, plan, authorities)
        TokenSvc->>TokenSvc: Sinh RefreshToken ngẫu nhiên & băm SHA-256 lưu DB
        TokenSvc->>TokenSvc: Ký JWT Access Token (15 phút)
        EmailLogin-->>Client: 200 OK {user, tokens}
    end
```

**Cơ chế chống Brute-force:**
- Mọi lần thử đăng nhập (kể cả thành công hay thất bại, email có tồn tại hay không) đều được lưu vào bảng `login_attempts` trong cùng transaction (có cờ `noRollbackFor = BusinessException.class` để bảo đảm dữ liệu lần thử luôn được lưu xuống DB).
- Nếu 5 lần đăng nhập gần nhất của cùng email đều thất bại (`succeeded = false`) trong khung thời gian bảo vệ, hệ thống lập tức từ chối và phản hồi `429 AUTH_ACCOUNT_LOCKED`.

---

## 3. Luồng Đăng nhập qua Google OAuth2 <a id="3-luong-google"></a>

```mermaid
sequenceDiagram
    autonumber
    actor Client as Client / User
    participant LoginCtrl as LoginController
    participant GoogleLogin as GoogleLoginImpl
    participant GoogleSvc as GoogleServiceImpl
    participant GoogleAPI as Google OAuth2 API
    participant UserSvc as UserService
    participant TokenSvc as TokenServiceImpl

    Client->>LoginCtrl: POST /auth/google {idToken}
    LoginCtrl->>GoogleLogin: login(req, clientInfo)
    GoogleLogin->>GoogleSvc: verifyGoogleToken(idToken)
    GoogleSvc->>GoogleAPI: Xác thực chữ ký và lấy Claims (email, sub, name, picture)
    alt Token không hợp lệ
        GoogleSvc-->>Client: 400 AUTH_GOOGLE_TOKEN_INVALID
    end

    GoogleLogin->>UserSvc: Tìm user theo googleId hoặc email
    alt Người dùng mới hoàn toàn
        GoogleLogin->>UserSvc: Tạo user mới (status=ACTIVE, googleId, name, avatar)
    else Đã có tài khoản bằng email nhưng chưa gắn googleId
        GoogleLogin->>UserSvc: Cập nhật googleId vào tài khoản
    end

    GoogleLogin->>TokenSvc: create(userId, email, ...)
    GoogleLogin-->>Client: 200 OK {user, tokens}
```

- Người dùng đăng nhập Google được tự động kích hoạt trạng thái `ACTIVE` (không cần xác thực OTP email).
- Ban đầu tài khoản chưa có mật khẩu (`password_hash = NULL`). Khi có nhu cầu, người dùng có thể dùng chức năng tạo mật khẩu (`POST /users/me/password`) để bổ sung mật khẩu đăng nhập email truyền thống.

---

## 4. Cơ chế Xoay vòng Refresh Token & Phát hiện Tái sử dụng (Token Rotation & Reuse Detection) <a id="4-co-che-token-rotation"></a>

Đây là cơ chế bảo mật then chốt theo chuẩn **OAuth 2.0 Token Exchange & Rotation**:

```mermaid
sequenceDiagram
    autonumber
    actor Client as Client / Kẻ tấn công
    participant AuthCtrl as AuthController
    participant AuthSvc as AuthServiceImpl
    participant TokenSvc as TokenServiceImpl
    participant RTRepo as RefreshTokenRepository

    Client->>AuthCtrl: POST /auth/refresh {refreshToken: RT_1}
    AuthCtrl->>AuthSvc: refresh(req)
    AuthSvc->>TokenSvc: checkRefreshAndGetUserId(RT_1)
    TokenSvc->>TokenSvc: hash = SHA-256(RT_1)
    TokenSvc->>RTRepo: findByTokenHashAndRevokedAtIsNull(hash)

    alt TH1: RT_1 hợp lệ và còn hạn
        TokenSvc->>RTRepo: Đánh dấu RT_1: revoked_at = now()
        TokenSvc-->>AuthSvc: Trả về userId
        AuthSvc->>TokenSvc: create(...) -> Phát hành RT_2 mới và AT_2 mới
        AuthSvc-->>Client: 200 OK {accessToken: AT_2, refreshToken: RT_2}
    else TH2: RT_1 đã bị thu hồi trước đó (Dấu hiệu Bị Đánh Cắp!)
        TokenSvc->>RTRepo: findByTokenHash(hash) -> Tìm thấy token đã thu hồi
        TokenSvc->>RTRepo: revokeAllActiveForUser(userId) -> Thu hồi TOÀN BỘ token của user!
        TokenSvc-->>Client: 401 AUTH_REFRESH_TOKEN_INVALID
    else TH3: RT_1 không tồn tại hoặc sai định dạng
        TokenSvc-->>Client: 401 AUTH_REFRESH_TOKEN_INVALID
    end
```

**Nguyên lý bảo vệ:**
1. Mỗi Refresh Token chỉ được sử dụng **đúng 1 lần duy nhất** để lấy token mới.
2. Khi sử dụng thành công, token cũ lập tức được đánh dấu `revoked_at = now()`.
3. Nếu kẻ tấn công đánh cắp được token cũ và cố tình gửi lên lại, hệ thống phát hiện token đã nằm trong danh sách thu hồi (`revoked_at != NULL`), lập tức kích hoạt biện pháp phòng thủ diện rộng: **Hủy toàn bộ phiên làm việc của người dùng trên tất cả thiết bị** (`revokeAllActiveForUser`), buộc người dùng chính chủ phải đăng nhập lại và đổi mật khẩu.

---

## 5. Luồng Quên & Đặt lại mật khẩu <a id="5-luong-quen-mat-khau"></a>

1. **Yêu cầu cấp mã (`POST /auth/forgot-password`):**
   - Rate Limiter kiểm tra tần suất gọi theo email trên Redis.
   - Kiểm tra email có tồn tại trong hệ thống.
   - Sinh mã OTP 6 chữ số lưu vào Redis với type `PASSWORD_RESET_OTP` (TTL 15 phút) và gửi email hướng dẫn.
2. **Đặt lại mật khẩu (`POST /auth/reset-password`):**
   - Xác thực mã OTP trong Redis.
   - Kiểm tra mật khẩu mới không được trùng với mật khẩu hiện tại (tránh đổi như không đổi).
   - Mã hóa mật khẩu mới bằng BCrypt và lưu vào CSDL.
   - Xóa mã OTP khỏi Redis.
   - Thu hồi toàn bộ Refresh Token của tài khoản trên mọi thiết bị (`tokenService.revokeAllByEmail(...)`) để buộc các phiên đăng nhập cũ phải xác thực lại bằng mật khẩu mới.

---

## 6. Luồng Đăng xuất phiên làm việc <a id="6-luong-dang-xuat"></a>

- **Đăng xuất thiết bị hiện tại (`logoutAllDevices = false`):**
  - Hash chuỗi `refreshToken` gửi kèm trong request.
  - Tìm bản ghi trong `refresh_tokens` và gán `revoked_at = now()`.
- **Đăng xuất tất cả thiết bị (`logoutAllDevices = true`):**
  - Lấy `userId` từ Security Context của Access Token.
  - Cập nhật `revoked_at = now()` cho toàn bộ các bản ghi `refresh_tokens` đang hoạt động của người dùng đó.

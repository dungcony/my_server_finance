# ĐẶC TẢ API — PHÂN HỆ XÁC THỰC (AUTH)

> **File này trả lời:** Các endpoint HTTP xác thực, định dạng Request/Response, quy tắc truyền token và các mã lỗi trả về.  
> Cấu trúc bảng & Entity → [lop-thuc-the.md](lop-thuc-the.md) · Luồng xử lý → [pipeline.md](pipeline.md) · Luật bảo mật → [rule.md](rule.md)

---

## 📑 MỤC LỤC

- [1. Quy ước chung](#1-quy-uoc-chung)
- [2. Danh mục Endpoint](#2-danh-muc-endpoint)
- [3. Chi tiết Endpoint Đăng nhập (LoginController)](#3-chi-tiet-dang-nhap)
  - [3.1 Đăng nhập bằng Email/Mật khẩu](#31-dang-nhap-email)
  - [3.2 Đăng nhập bằng Google OAuth2](#32-dang-nhap-google)
- [4. Chi tiết Endpoint Quản lý Xác thực (AuthController)](#4-chi-tiet-xac-thuc)
  - [4.1 Đăng ký tài khoản](#41-dang-ky)
  - [4.2 Xác thực Email qua OTP](#42-xac-thuc-email)
  - [4.3 Gửi lại mã kích hoạt Email](#43-gui-lai-ma)
  - [4.4 Làm mới Access Token (Token Refresh)](#44-lam-moi-token)
  - [4.5 Đăng xuất](#45-dang-xuat)
  - [4.6 Yêu cầu quên mật khẩu](#46-quen-mat-khau)
  - [4.7 Đặt lại mật khẩu mới](#47-dat-lai-mat-khau)
- [5. Bảng mã lỗi đặc thù (Error Codes)](#5-bang-ma-loi)

---

## 1. Quy ước chung <a id="1-quy-uoc-chung"></a>

- **Base URL:** `/auth`
- **Định dạng dữ liệu:** `Content-Type: application/json`, `Accept: application/json`
- **Đóng gói chuẩn:** Mọi response trả về đều bọc trong cấu trúc `ApiResponse<T>`:
  ```json
  {
    "code": 200,
    "message": "Thành công",
    "data": { ... }
  }
  ```
- **Xác thực phiên:** Các endpoint công khai không yêu cầu header `Authorization`. Endpoint `/auth/logout` nhận `Authorization: Bearer <accessToken>` từ ngữ cảnh phiên hiện tại.
- **Client Metadata:** Tự động trích xuất địa chỉ IP (`X-Forwarded-For` / `RemoteAddr`) và thiết bị (`User-Agent`) để lưu vào lịch sử đăng nhập.

---

## 2. Danh mục Endpoint <a id="2-danh-muc-endpoint"></a>

| Phương thức | Endpoint | Controller | Quyền hạn | Mô tả |
| :--- | :--- | :--- | :--- | :--- |
| `POST` | `/auth/login` | `LoginController` | Công khai | Đăng nhập bằng email và mật khẩu |
| `POST` | `/auth/google` | `LoginController` | Công khai | Đăng nhập/Đăng ký tự động qua Google `idToken` |
| `POST` | `/auth/register` | `AuthController` | Công khai | Đăng ký tài khoản mới với email/mật khẩu |
| `POST` | `/auth/verify-email` | `AuthController` | Công khai | Xác thực mã OTP kích hoạt tài khoản |
| `POST` | `/auth/resend-verification` | `AuthController` | Công khai | Gửi lại mã OTP kích hoạt tài khoản |
| `POST` | `/auth/refresh` | `AuthController` | Công khai | Cấp mới Access Token bằng Refresh Token (Token Rotation) |
| `POST` | `/auth/logout` | `AuthController` | Bearer Token | Đăng xuất thiết bị hiện tại hoặc tất cả thiết bị |
| `POST` | `/auth/forgot-password` | `AuthController` | Công khai | Gửi mã OTP yêu cầu đặt lại mật khẩu |
| `POST` | `/auth/reset-password` | `AuthController` | Công khai | Đặt lại mật khẩu mới bằng mã OTP xác thực |

---

## 3. Chi tiết Endpoint Đăng nhập (LoginController) <a id="3-chi-tiet-dang-nhap"></a>

### 3.1 Đăng nhập bằng Email/Mật khẩu <a id="31-dang-nhap-email"></a>

- **Method / Path:** `POST /auth/login`
- **Mô tả:** Xác thực thông tin tài khoản, ghi vết đăng nhập vào `login_attempts`. Nếu sai quá 5 lần liên tiếp trong khoảng thời gian quy định sẽ tạm khóa tài khoản.

**Request Body (`EmailLoginRequest`):**
```json
{
  "email": "user@example.com",
  "password": "Password123@"
}
```

**Response (`200 OK` - `ApiResponse<AuthRes>`):**
```json
{
  "code": 200,
  "message": "Thành công",
  "data": {
    "user": {
      "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "email": "user@example.com",
      "firstName": "Nguyễn",
      "lastName": "Văn A",
      "avatarUrl": "https://example.com/avatar.png",
      "plan": "FREE",
      "status": "ACTIVE"
    },
    "tokens": {
      "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
      "refreshToken": "4a7b1c...",
      "expiresIn": 900
    }
  }
}
```

**Mã lỗi thường gặp:**
- `400 AUTH_CREDENTIALS_INVALID`: Email hoặc mật khẩu không chính xác.
- `403 AUTH_ACCOUNT_BLOCKED`: Tài khoản đã bị quản trị viên khóa.
- `403 AUTH_ACCOUNT_NOT_VERIFIED`: Tài khoản chưa xác thực email qua mã OTP.
- `429 AUTH_ACCOUNT_LOCKED`: Đăng nhập sai quá 5 lần, tạm thời bị khóa.

---

### 3.2 Đăng nhập bằng Google OAuth2 <a id="32-dang-nhap-google"></a>

- **Method / Path:** `POST /auth/google`
- **Mô tả:** Xác thực qua Google Id Token. Nếu email chưa tồn tại, hệ thống tự động tạo tài khoản mới với trạng thái `ACTIVE` mà không cần xác thực OTP.

**Request Body (`GoogleLoginRequest`):**
```json
{
  "idToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6IjFkZ..."
}
```

**Response (`200 OK` - `ApiResponse<AuthRes>`):**
Cấu trúc tương tự như đăng nhập bằng Email/Mật khẩu.

**Mã lỗi thường gặp:**
- `400 AUTH_GOOGLE_TOKEN_INVALID`: Id Token không hợp lệ hoặc đã hết hạn từ Google.
- `403 AUTH_ACCOUNT_BLOCKED`: Tài khoản đã bị khóa.

---

## 4. Chi tiết Endpoint Quản lý Xác thực (AuthController) <a id="4-chi-tiet-xac-thuc"></a>

### 4.1 Đăng ký tài khoản <a id="41-dang-ky"></a>

- **Method / Path:** `POST /auth/register`
- **Mô tả:** Khởi tạo người dùng ở trạng thái `PENDING`, phát sinh mã OTP 6 chữ số lưu vào Redis và gửi email xác nhận.

**Request Body (`RegisterRequest`):**
```json
{
  "email": "newuser@example.com",
  "password": "Password123@"
}
```

**Response (`201 Created` - `ApiResponse<RegisterRes>`):**
```json
{
  "code": 201,
  "message": "Thành công",
  "data": {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "email": "newuser@example.com"
  }
}
```

**Mã lỗi thường gặp:**
- `409 AUTH_EMAIL_ALREADY_EXISTS`: Địa chỉ email đã được sử dụng bởi người dùng khác.
- `400 INVALID_ARGUMENT`: Mật khẩu không đáp ứng tiêu chuẩn độ phức tạp (tối thiểu 8 ký tự, có chữ hoa, số, ký tự đặc biệt).

---

### 4.2 Xác thực Email qua OTP <a id="42-xac-thuc-email"></a>

- **Method / Path:** `POST /auth/verify-email`
- **Mô tả:** Kiểm tra mã OTP trong Redis. Nếu đúng, kích hoạt tài khoản sang `ACTIVE`, xóa OTP và phát hành cặp Token đăng nhập ngay lập tức.

**Request Body (`VerifyEmailRequest`):**
```json
{
  "email": "newuser@example.com",
  "code": "123456"
}
```

**Response (`200 OK` - `ApiResponse<AuthRes>`):**
Trả về thông tin tài khoản và cặp token `accessToken`, `refreshToken`.

**Mã lỗi thường gặp:**
- `400 AUTH_CODE_INVALID`: Mã OTP không chính xác, đã hết hạn hoặc đã bị xóa do nhập sai quá 5 lần.

---

### 4.3 Gửi lại mã kích hoạt Email <a id="43-gui-lai-ma"></a>

- **Method / Path:** `POST /auth/resend-verification`
- **Mô tả:** Tạo mã OTP mới và gửi lại email kích hoạt. Bắt buộc giãn cách tối thiểu 60 giây giữa các lần yêu cầu.

**Request Body (`ResendVerificationRequest`):**
```json
{
  "email": "newuser@example.com"
}
```

**Response (`200 OK`):**
```json
{
  "code": 200,
  "message": "Thành công",
  "data": {
    "message": "Mã xác thực mới đã được gửi tới email của bạn."
  }
}
```

**Mã lỗi thường gặp:**
- `400 AUTH_ACCOUNT_ALREADY_VERIFIED`: Tài khoản đã được kích hoạt trước đó.
- `429 RATE_LIMIT_EXCEEDED`: Vui lòng chờ 60 giây trước khi yêu cầu gửi lại mã.

---

### 4.4 Làm mới Access Token (Token Refresh) <a id="44-lam-moi-token"></a>

- **Method / Path:** `POST /auth/refresh`
- **Mô tả:** Đổi Refresh Token cũ lấy cặp Access Token và Refresh Token mới (Refresh Token Rotation). Nếu phát hiện token đã bị thu hồi trước đó được gửi lên lại, hệ thống sẽ thu hồi toàn bộ phiên hoạt động của người dùng để chống tấn công đánh cắp token.

**Request Body (`RefreshRequest`):**
```json
{
  "refreshToken": "4a7b1c..."
}
```

**Response (`200 OK` - `ApiResponse<TokenRes>`):**
```json
{
  "code": 200,
  "message": "Thành công",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "9d8e7f...",
    "expiresIn": 900
  }
}
```

**Mã lỗi thường gặp:**
- `401 AUTH_REFRESH_TOKEN_INVALID`: Refresh Token không tồn tại, đã hết hạn, hoặc bị thu hồi.
- `403 AUTH_ACCOUNT_BLOCKED`: Tài khoản người dùng đã bị khóa.

---

### 4.5 Đăng xuất <a id="45-dang-xuat"></a>

- **Method / Path:** `POST /auth/logout`
- **Header:** `Authorization: Bearer <accessToken>`
- **Mô tả:** Đánh dấu thu hồi (`revoked_at = now()`) Refresh Token hiện tại, hoặc thu hồi toàn bộ Refresh Tokens của người dùng nếu chọn `logoutAllDevices = true`.

**Request Body (`LogoutRequest`):**
```json
{
  "refreshToken": "4a7b1c...",
  "logoutAllDevices": false
}
```

**Response (`200 OK`):**
```json
{
  "code": 200,
  "message": "Thành công",
  "data": null
}
```

---

### 4.6 Yêu cầu quên mật khẩu <a id="46-quen-mat-khau"></a>

- **Method / Path:** `POST /auth/forgot-password`
- **Mô tả:** Gửi mã OTP xác nhận đặt lại mật khẩu về email. Có kiểm tra rate limit trên Redis.

**Request Body (`ForgotPasswordRequest`):**
```json
{
  "email": "user@example.com"
}
```

**Response (`200 OK`):**
```json
{
  "code": 200,
  "message": "Thành công",
  "data": null
}
```

**Mã lỗi thường gặp:**
- `404 USER_NOT_FOUND`: Email không tồn tại trong hệ thống.
- `429 RATE_LIMIT_EXCEEDED`: Yêu cầu vượt quá hạn mức cho phép trong khoảng thời gian quy định.

---

### 4.7 Đặt lại mật khẩu mới <a id="47-dat-lai-mat-khau"></a>

- **Method / Path:** `POST /auth/reset-password`
- **Mô tả:** Xác thực mã OTP và cập nhật mật khẩu mới. Tự động thu hồi toàn bộ Refresh Token của tài khoản trên mọi thiết bị để bảo đảm an toàn.

**Request Body (`ResetPasswordRequest`):**
```json
{
  "email": "user@example.com",
  "resetCode": "654321",
  "newPassword": "NewPassword123@"
}
```

**Response (`200 OK`):**
```json
{
  "code": 200,
  "message": "Thành công",
  "data": null
}
```

**Mã lỗi thường gặp:**
- `400 AUTH_CODE_INVALID`: Mã OTP không chính xác hoặc đã hết hạn.
- `400 AUTH_PASSWORD_SAME_AS_OLD`: Mật khẩu mới không được trùng với mật khẩu hiện tại.

---

## 5. Bảng mã lỗi đặc thù (Error Codes) <a id="5-bang-ma-loi"></a>

| Mã lỗi kỹ thuật | HTTP Status | Thông điệp người dùng |
| :--- | :---: | :--- |
| `AUTH_CREDENTIALS_INVALID` | 400 | Thông tin đăng nhập không chính xác |
| `AUTH_EMAIL_ALREADY_EXISTS` | 409 | Email này đã được đăng ký tài khoản |
| `AUTH_CODE_INVALID` | 400 | Mã xác thực không chính xác hoặc đã hết hạn |
| `AUTH_ACCOUNT_NOT_VERIFIED` | 403 | Tài khoản chưa được kích hoạt qua email |
| `AUTH_ACCOUNT_ALREADY_VERIFIED` | 400 | Tài khoản đã được kích hoạt trước đó |
| `AUTH_ACCOUNT_BLOCKED` | 403 | Tài khoản đã bị tạm khóa bởi quản trị viên |
| `AUTH_ACCOUNT_LOCKED` | 429 | Đăng nhập sai quá nhiều lần. Vui lòng thử lại sau |
| `AUTH_REFRESH_TOKEN_INVALID` | 401 | Phiên đăng nhập không hợp lệ hoặc đã hết hạn |
| `AUTH_GOOGLE_TOKEN_INVALID` | 400 | Mã xác thực Google không hợp lệ |
| `RATE_LIMIT_EXCEEDED` | 429 | Thao tác quá nhanh, vui lòng chờ trong giây lát |

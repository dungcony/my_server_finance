# Workflow — Người dùng (auth, user)

Code chính: [AuthServiceImpl](../../src/main/java/com/datn/financeapp/module/auth/service/impl/AuthServiceImpl.java),
[UserServiceImpl](../../src/main/java/com/datn/financeapp/module/user/service/impl/UserServiceImpl.java),
[EmailLoginImpl](../../src/main/java/com/datn/financeapp/module/auth/service/impl/EmailLoginImpl.java),
[GoogleLoginImpl](../../src/main/java/com/datn/financeapp/module/auth/service/impl/GoogleLoginImpl.java).

Trigger liên quan (migration_v2):

| Trigger | File | Khi nào chạy |
|---|---|---|
| `trg_users_default_wallet` | [V6](../../src/main/resources/db/migration_v2/V6__trigger_wallet_default.sql) | Có user mới → tạo ví "Tiền mặt" mặc định |
| `trg_wallets_only_one_default` | [V6](../../src/main/resources/db/migration_v2/V6__trigger_wallet_default.sql) | Lúc commit, kiểm mỗi user đúng 1 ví mặc định |
| `trg_wallets_initial_transaction` | [V7](../../src/main/resources/db/migration_v2/V7__trigger_wallet_balance.sql) | Ví mới có số dư > 0 → sinh giao dịch số dư ban đầu (ví mặc định số dư 0 nên không sinh) |

## Đăng ký bằng email — `POST /auth/register`

```
register
-> kiểm email đã có chưa (có → AUTH_EMAIL_ALREADY_EXISTS)
-> insert users (status = pending_verify, plan = free, mật khẩu băm bcrypt)
   -> [trigger] tạo ví "Tiền mặt" (CASH, mặc định, icon default_wallet, số dư 0)
   -> [trigger] số dư 0 nên không sinh giao dịch
   -> [trigger, lúc commit] kiểm đúng 1 ví mặc định
-> gán ROLE_USER (user_roles)
-> bắn UserCreateEvent (hiện chưa có listener nào nghe)
-> sinh OTP 6 số, lưu Redis (hết hạn sau 15 phút)
-> gửi email OTP (bất đồng bộ, @Async)
-> trả { user_id, email }
```

## Xác thực email — `POST /auth/verify-email`

```
verify-email
-> lấy OTP trong Redis theo (REGISTER_OTP, email) (không có → AUTH_CODE_INVALID)
-> sai mã → tăng số lần sai, đủ 5 lần thì huỷ OTP; báo AUTH_CODE_INVALID
-> đúng mã → xoá OTP
-> users.status = active
-> cấp access token + refresh token
```

Gửi lại mã — `POST /auth/resend-verification`: tài khoản bị khoá hoặc đã xác thực thì báo lỗi; mã cũ chưa quá 60 giây thì báo `RATE_LIMIT_EXCEEDED`; còn lại sinh OTP mới và gửi email.

## Đăng nhập email — `POST /auth/login`

```
login
-> email chuyển chữ thường
-> 5 lần đăng nhập gần nhất đều sai và lần sai cuối trong 15 phút → AUTH_ACCOUNT_LOCKED
-> tìm user, so mật khẩu
-> ghi login_attempts (đúng/sai, IP, user agent)
-> sai → AUTH_CREDENTIALS_INVALID
-> bị khoá → AUTH_ACCOUNT_BLOCKED
-> chưa xác thực email → AUTH_ACCOUNT_NOT_VERIFIED
-> cấp access token + refresh token
```

## Đăng nhập Google — `POST /auth/google`

```
google
-> xác thực id token với Google
-> tìm user theo email
   -> chưa có → tạo user active (+ trigger tạo ví mặc định như đăng ký) + gán ROLE_USER
   -> có rồi, chưa gắn Google → gắn google_id
   -> có rồi, google_id khác → AUTH_GOOGLE_TOKEN_INVALID
   -> đã xoá / bị khoá → báo lỗi
   -> status = active (Google đã xác thực email)
-> cấp access token + refresh token
```

## Quên mật khẩu

```
POST /auth/forgot-password
-> đếm lượt theo email (quá giới hạn → chặn, email lạ cũng tốn lượt)
-> user phải tồn tại, không bị khoá, đã xác thực
-> sinh OTP đặt lại mật khẩu, lưu Redis, gửi email

POST /auth/reset-password
-> kiểm OTP (sai 5 lần thì huỷ)
-> đổi mật khẩu (trùng mật khẩu cũ → AUTH_PASSWORD_SAME_AS_OLD)
-> xoá OTP
-> thu hồi mọi refresh token theo email (đăng xuất mọi thiết bị)
```

## Nâng cấp Premium

User tự nâng cấp bằng `POST /users/me/premium-upgrades`; thanh toán qua payOS, giao hàng khi nhận `PaymentPaidEvent`. `users.plan` đổi sang `premium` và `users.plan_expires_at` = hạn mới; job `PremiumExpiryJob` hạ về `free` khi hết hạn. Luồng đầy đủ xem [payment.md](payment.md).

## Thu hồi phiên khi tài khoản thay đổi

[AuthEventListener](../../src/main/java/com/datn/financeapp/module/auth/events/listener/AuthEventListener.java) nghe event từ module user và thu hồi toàn bộ refresh token của user:

| Event | Bắn khi |
|---|---|
| `UserPasswordChangedEvent` | Người dùng đổi mật khẩu |
| `UserDeletedEvent` | Người dùng tự xoá hoặc admin xoá tài khoản (xoá mềm) |
| `UserLockedEvent` | Admin khoá tài khoản |

## Lỗi đang mở (xem [review-06](../problem/review-06-user-auth.md))

- U2: đăng ký email có chữ hoa bị lỗi (bảng `users` bắt email chữ thường, nhưng `register` chưa chuyển).
- U3: `verify-email` không kiểm tài khoản bị khoá; `resend-verification` báo lỗi lộ trạng thái tài khoản.

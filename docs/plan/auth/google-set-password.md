# Plan: Tài khoản đăng nhập Google trước — API tự sinh mật khẩu và gửi về email

- **Phạm vi:** module `user` (service, controller) + `common/mail`
- **Trạng thái:** ✅ ĐÃ LÀM XONG (code + test + tài liệu + bruno)
- **Xác nhận:** `[x] PROCESS`
- **Quyết định của bạn (đã thay phương án A/B ở bản trước):** thêm API mới, không truyền gì ngoài token; nếu
  `password == null` thì cho phép; sinh mật khẩu rồi gửi về email.

---

## 0. Nguyên nhân (giữ lại để đối chiếu)

Tài khoản tạo bằng Google có `password_hash = NULL` (`AccountServiceImpl.createGoogleUser`). Đường đổi mật khẩu chặn
bằng `NO_PASSWORD_SET`, quên mật khẩu im lặng bỏ qua, đăng ký trùng email bị từ chối → không có cách nào đặt mật khẩu
lần đầu. API mới bổ sung đúng đường còn thiếu đó.

---

## 1. Các file sẽ thay đổi

| File                                                       | Thay đổi                                                                                       |
| ---------------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| `user/controller/UserController.java`                      | Thêm `POST /me/password` (không body) → gọi `AccountService.generatePassword(userId)`         |
| `user/service/AccountService.java`                         | Thêm `void generatePassword(UUID userId)`                                                      |
| `user/service/impl/AccountServiceImpl.java`                | Hiện thực: kiểm `password == null` → sinh mật khẩu → băm, lưu → gửi mail                       |
| `user/exception/PasswordAlreadySetException.java` + `ErrorCode` | Lỗi khi tài khoản **đã có** mật khẩu: `PASSWORD_ALREADY_SET` (409)                         |
| `common/mail/EmailService.java` + `SmtpEmailServiceImpl` + `LogEmailServiceImpl` | Thêm `sendGeneratedPassword(email, rawPassword)`                          |
| `common/util/` (hoặc `user/helper/`)                       | Hàm sinh mật khẩu ngẫu nhiên bằng `SecureRandom` (thành class riêng để test được)             |
| `user/dto/response/UserProfileResponse.java` + `user/mapper/UserMapper.java` | Thêm `hasPassword` (JSON `has_password`); mapper `toProfile` thêm `@Mapping(target = "hasPassword", expression = "java(user.getPassword() != null)")` — `getMe` và `updateMe` cùng dùng `toProfile` nên cả hai đều có cờ |
| `api/01-EXTEND-USER-PROFILE.md`, `THIET-KE-PHAN-QUYEN.md` (dòng 234) | Thêm mục API mới; ghi `NO_PASSWORD_SET` ở đổi MK vẫn đúng, trỏ sang API mới làm lối ra |
| Test (mục 3)                                               | Thêm test                                                                                      |

Không đụng: schema CSDL (`password_hash` đã nullable từ V16), `register`, `loginWithGoogle`, `changePassword`,
`forgotPassword`.

---

## 2. Hướng giải quyết

### Endpoint

```
POST /users/me/password
Authorization: Bearer <access_token>
(không có body)
→ 200 { "success": true, "data": null, "msg": "Mật khẩu đã được gửi về email của bạn." }
```

Dùng `POST` vì là **tạo** mật khẩu lần đầu; `PUT` cùng đường dẫn vẫn là đổi mật khẩu. Không có `Idempotency-Key`: gọi
lần 2 bị từ chối vì mật khẩu đã tồn tại, nên tự an toàn.

| Trường hợp                         | Kết quả                                        |
| ---------------------------------- | ---------------------------------------------- |
| `password == null`                 | Sinh, lưu, gửi mail → 200                      |
| `password != null`                 | 409 `PASSWORD_ALREADY_SET`                     |
| Tài khoản bị khoá / đã xoá         | Như mọi API khác: bị chặn từ cửa xác thực (5 cửa) |

### Code xem trước

```java
// AccountServiceImpl
@Transactional
@Override
public void generatePassword(UUID userId) {
    User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);

    // chỉ tài khoản chưa có mật khẩu (đăng nhập Google thuần) mới được tạo
    if (user.getPassword() != null) {
        throw new PasswordAlreadySetException();
    }

    String rawPassword = passwordGenerator.generate();
    user.setPassword(passwordEncoder.encode(rawPassword));
    userRepository.save(user);

    emailService.sendGeneratedPassword(user.getEmail(), rawPassword);
}
```

- `PasswordGenerator`: 12 ký tự, `SecureRandom`, đảm bảo có chữ hoa, chữ thường, số; bỏ ký tự dễ nhầm (`0/O`, `1/l/I`) cho
  dễ gõ. Đạt ràng buộc tối thiểu 8 ký tự của `UpdatePassReq`.
- Không phát `UserPasswordChangedEvent`: sự kiện này thu hồi refresh token, mà người dùng vừa đặt mật khẩu lần đầu
  không có "mật khẩu cũ bị lộ" nào cần cắt, và sẽ làm họ văng khỏi thiết bị đang dùng.
- **Không log mật khẩu thô** ở `SmtpEmailServiceImpl`. `LogEmailServiceImpl` (profile test) in nội dung — chấp nhận được vì
  chỉ chạy khi test.

### Rủi ro bạn nên biết (chưa chặn, chờ bạn quyết ở Q1–Q2)

1. **Mật khẩu thô nằm trong hộp thư.** Ai đọc được email là biết mật khẩu, và nó nằm lại trong hộp thư mãi. Đây là
   cách đơn giản nhất bạn chọn, nên plan làm theo; email sẽ nhắc người dùng "hãy đổi mật khẩu ngay" (đã có
   `PUT /me/password`, giờ dùng được vì `password != null`).
2. **Gửi mail là `@Async` và nuốt lỗi SMTP** (`SmtpEmailServiceImpl.sendEmail` chỉ log). Nếu SMTP lỗi, DB đã đặt mật khẩu
   mà người dùng không nhận được gì. Lối thoát: `forgot-password` lúc này chạy được (vì `password != null`). Nếu muốn chắc
   hơn thì gửi mail **sau commit** (`@TransactionalEventListener(AFTER_COMMIT)`), tránh mail đã đi mà DB rollback.
3. **Access token bị lộ → kẻ cắp đặt được mật khẩu**, nhưng mật khẩu chỉ gửi về email chủ tài khoản nên kẻ cắp không đọc
   được. Rủi ro thấp, và chính là lý do hợp lý để gửi qua email thay vì trả trong response.

---

## 3. Test (TDD — đỏ trước)

| Test                                                                                          | Loại        |
| --------------------------------------------------------------------------------------------- | ----------- |
| `password == null` → lưu hash (không phải chuỗi thô), gọi `emailService.sendGeneratedPassword` đúng email/mật khẩu | Unit |
| `password != null` → ném `PasswordAlreadySetException`, không gửi mail, không đổi hash        | Unit        |
| `PasswordGenerator`: đủ độ dài, đủ nhóm ký tự, hai lần gọi khác nhau                          | Unit        |
| Đăng nhập Google → `POST /me/password` → `login` email + mật khẩu nhận được qua mail thành công | Integration |
| Gọi lần hai → 409 `PASSWORD_ALREADY_SET`                                                      | Integration |
| `GET /users/me` trả `has_password=false` cho tài khoản Google thuần, `true` cho tài khoản email; sau khi gọi `POST /me/password` thì thành `true` | Integration |
| Không có token → 401                                                                          | Integration |
| Tài khoản email thường (đã có MK) gọi → 409, mật khẩu cũ vẫn đăng nhập được                   | Integration |

Chạy: chỉ test thuộc `user`/`common/mail` liên quan, không chạy full.

---

## 4. Đã chốt

- **Q1.** ✅ Đường dẫn `POST /users/me/password`.
- **Q2.** ✅ Gọi thẳng `emailService` trong service, không dùng event sau commit.
- **Q3.** ✅ Thêm `has_password` vào hồ sơ (`GET`/`PATCH /users/me`).
- **Phạm vi:** chỉ backend (`source/server/`). Không đụng app Flutter. Alias `/auth` trên `UserController` giữ nguyên.

# Sửa các lỗi phân hệ User và Auth (U2 - U3) & Bổ sung Admin Unlock User

## Bối cảnh

Tài liệu [review-06-user-auth.md](file:///D:/projects/java/server/docs/problem/review-06-user-auth.md) rà soát mã nguồn phân hệ User & Auth và chỉ ra 2 vấn đề:
- **U2 (Mức Nặng):** Đăng ký email có chữ in hoa bị lỗi 500 do vi phạm ràng buộc CSDL `ck_users_email CHECK (email = lower(email))` tại [V1__auth_user_role_permission.sql:24](file:///D:/projects/java/server/src/main/resources/db/migration_v2/V1__auth_user_role_permission.sql#L24). Khi đăng nhập thì [EmailLoginImpl.java:39](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/auth/service/impl/EmailLoginImpl.java#L39) lại dùng `toLowerCase().trim()`, dẫn tới lệch logic. Người dùng yêu cầu chuẩn hoá `toLowerCase()` nhưng **bỏ qua `.trim()`**, yêu cầu người dùng/client phải nhập chuẩn.
- **U3 (Mức Nhẹ):** 
  - Kích hoạt tài khoản qua `verifyEmail` ([AuthServiceImpl.java:78](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/auth/service/impl/AuthServiceImpl.java#L78)) gọi `userService.updateStatus(email, ACTIVE)`, đặt thẳng trạng thái `ACTIVE` mà không kiểm tra tài khoản có đang bị khoá (`BLOCKED`) hay không ([UserServiceImpl.java:105](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/user/service/impl/UserServiceImpl.java#L105)). Điều này dẫn đến nguy cơ tài khoản đã bị admin khoá vẫn có thể tự mở khoá nếu mã OTP kích hoạt cũ còn hạn.
  - Đoạn code `resendVerification` ([AuthServiceImpl.java:94-104](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/auth/service/impl/AuthServiceImpl.java#L94)) có comment mâu thuẫn với thiết kế chuẩn trong [api.md](file:///D:/projects/java/server/docs/design/auth/api.md#L195) và [user.md](file:///D:/projects/java/server/docs/work_flow/user.md#L43).
- **Yêu cầu bổ sung từ User:** Bổ sung tính năng Admin mở khoá tài khoản người dùng (`unlockUser`) để hoàn thiện chu trình quản trị Admin (`lock` $\leftrightarrow$ `unlock`).

## Quyết định đã chốt

- **Chuẩn hoá email (U2):**
  - Chuyển `toLowerCase()` trên email đầu vào tại tất cả các phương thức của `AuthServiceImpl` (`register`, `verifyEmail`, `resendVerification`, `forgotPassword`, `resetPassword`).
  - **Không dùng `.trim()`**: Bỏ hoàn toàn `.trim()` ở cả `AuthServiceImpl` và `EmailLoginImpl.java:39` theo đúng chỉ đạo: *"bỏ qua .trim() yêu cầu user phải nhập chuẩn"*.
  - DTO request (`RegisterRequest`, `VerifyEmailRequest`, `ResendVerificationRequest`, `ForgotPasswordRequest`, `ResetPasswordRequest`) đã có annotation `@NotBlank` và `@Email` của Jakarta Validation.
- **Chặn mở khoá tài khoản bị khoá khi kích hoạt email (U3):**
  - Trong `UserServiceImpl.updateStatus(email, status)`, kiểm tra nếu `user.isLocked()` (tức `status == BLOCKED`) thì ném `BusinessException(ErrorCode.AUTH_ACCOUNT_BLOCKED)`.
- **Đồng bộ luồng `resendVerification` (U3):**
  - Chuẩn hoá comment trong `AuthServiceImpl.resendVerification`: Tuân thủ đặc tả thiết kế tại `docs/design/auth/api.md` (nếu tài khoản đã kích hoạt ném `AUTH_ACCOUNT_ALREADY_VERIFIED`, nếu tài khoản bị khoá ném `AUTH_ACCOUNT_BLOCKED`, kiểm tra cooldown 60s ném `RATE_LIMIT_EXCEEDED`). Xoá comment lỗi thời về việc "im lặng không báo lỗi".
- **Bổ sung tính năng Admin mở khóa tài khoản (`Admin Unlock User`):**
  - DTO mới: `UnlockUserRequest(UUID userId)`.
  - Service: Thêm `unlockUser(UUID managerId, UnlockUserRequest req)` vào `ManagerUserService` và cài đặt trong `ManagerUserServiceImpl`:
    - Kiểm tra `targetLevel > managerLevel` (không mở khóa tài khoản cấp cao hơn hoặc bằng mình).
    - Kiểm tra tài khoản phải đang ở trạng thái `BLOCKED`.
    - Chuyển `status` thành `ACTIVE` và lưu vào DB.
    - Xóa `userId` khỏi Redis Blacklist (`blacklistedUserRepository.deleteById(userId.toString())`).
    - Làm mới cache quyền (`userAuthCacheHelper.evictUserAuth(userId)`).
  - Controller: Thêm endpoint `@PatchMapping("/unlock")` vào `ManagerUserController` với quyền `USERS_UPDATE`.

---

## 1. Các file sẽ thay đổi

### Code hiện có
- `src/main/java/com/datn/financeapp/module/auth/service/impl/AuthServiceImpl.java`:
  - `register` (dòng 51-66): Chuyển `req.email().toLowerCase()` trước khi kiểm tra tồn tại và tạo user (U2).
  - `verifyEmail` (dòng 74-88): Chuyển `req.email().toLowerCase()` trước khi xác thực OTP và kích hoạt user (U2).
  - `resendVerification` (dòng 90-115): Chuyển `req.email().toLowerCase()`, cập nhật comment cho đúng thiết kế (U2, U3).
  - `forgotPassword` (dòng 150-158): Chuyển `req.email().toLowerCase()` trước khi rate limit và kiểm tra user (U2).
  - `resetPassword` (dòng 160-168): Chuyển `req.email().toLowerCase()` trước khi xác thực và cập nhật mật khẩu (U2).
- `src/main/java/com/datn/financeapp/module/auth/service/impl/EmailLoginImpl.java:39`:
  - Đổi `String email = req.email().toLowerCase().trim();` thành `String email = req.email().toLowerCase();` (bỏ `.trim()` theo yêu cầu U2).
- `src/main/java/com/datn/financeapp/module/user/service/impl/UserServiceImpl.java:105`:
  - Trong `updateStatus`, bổ sung kiểm tra `if (user.isLocked()) throw new BusinessException(ErrorCode.AUTH_ACCOUNT_BLOCKED);` để chặn việc mở khoá tài khoản bị khoá qua OTP (U3).

### Bổ sung mới cho tính năng Admin Unlock
- `src/main/java/com/datn/financeapp/module/user/dto/request/UnlockUserRequest.java`:
  - Tạo mới DTO record `UnlockUserRequest(@NotNull UUID userId)`.
- `src/main/java/com/datn/financeapp/common/security/BlacklistedUserRepository.java`:
  - Thêm default method helper `remove(UUID userId)` (gọi `deleteById(userId.toString())`).
- `src/main/java/com/datn/financeapp/module/user/service/ManagerUserService.java`:
  - Thêm chữ ký `void unlockUser(UUID managerId, UnlockUserRequest req);`.
- `src/main/java/com/datn/financeapp/module/user/service/impl/ManagerUserServiceImpl.java`:
  - Cài đặt `unlockUser`: kiểm tra quyền hạn cấp bậc, chuyển status sang `ACTIVE`, xóa Redis blacklist, xóa auth cache.
- `src/main/java/com/datn/financeapp/module/user/controller/ManagerUserController.java`:
  - Bổ sung `@PatchMapping("/unlock")` với `@PreAuthorize("hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).USERS_UPDATE.getValue())")`.

### Test
- `src/test/java/com/datn/financeapp/module/auth/controller/AuthEmailVerificationIntegrationTest.java`:
  - Thêm test case đăng ký với email có chữ in hoa (VD `Test.User@Example.COM`) thành công mà không bị lỗi DB constraint (U2).
  - Thêm test case verify email khi user bị admin khoá (`BLOCKED`) trả về lỗi `AUTH_ACCOUNT_BLOCKED` (U3).
- `src/test/java/com/datn/financeapp/module/user/controller/AdminBlockUserIntegrationTest.java`:
  - Thêm test case Admin mở khóa thành công: status chuyển sang `ACTIVE`, Redis blacklist bị xóa bỏ, người dùng có thể đăng nhập hoặc gọi API bình thường.
  - Thêm test case Admin không thể mở khóa người dùng có quyền cao hơn hoặc bằng mình.
  - Thêm test case mở khóa tài khoản không bị khóa trả về lỗi hợp lệ.

---

## 2. Hướng giải quyết / code xem trước

### Bước 1: Chuẩn hoá email tại `AuthServiceImpl.java` (bỏ `.trim()`, chỉ `toLowerCase()`)
```java
@Override
public RegisterRes register(RegisterRequest req) {
    Instant now = Instant.now();
    String email = req.email().toLowerCase();

    if (userService.existByEmail(email))
        throw new BusinessException(ErrorCode.AUTH_EMAIL_ALREADY_EXISTS);

    UserRes user = userService.create(
            UserCreateReq.forEmail(
                    email,
                    passwordEncoder.encode(req.password())
            )
    );
    sendEmail(email, OtpType.REGISTER_OTP, now);

    return new RegisterRes(
            user.id(),
            email
    );
}
```

Áp dụng tương tự cho `verifyEmail`, `resendVerification`, `forgotPassword`, `resetPassword`:
```java
String email = req.email().toLowerCase();
```

### Bước 2: Bỏ `.trim()` tại `EmailLoginImpl.java:39`
```java
@Transactional(noRollbackFor = BusinessException.class)
@Override
public AuthRes login(EmailLoginRequest req, ClientInfo client) {
    String email = req.email().toLowerCase();
    ...
```

### Bước 3: Chặn kích hoạt tài khoản đã bị khoá tại `UserServiceImpl.java:105`
```java
@Override
public UserRes updateStatus(String email, UserStatus status) {
    User user = userRepository.findByEmail(email)
            .filter(u -> !u.getIsDeleted())
            .orElseThrow(UserNotFoundException::new);

    if (user.isLocked())
        throw new BusinessException(ErrorCode.AUTH_ACCOUNT_BLOCKED);

    user.setStatus(status);

    return userMapper.toResponse(userRepository.save(user));
}
```

### Bước 4: Tạo DTO `UnlockUserRequest.java`
```java
package com.datn.financeapp.module.user.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record UnlockUserRequest(
        @NotNull(message = "không được để trống người dùng")
        UUID userId
) {
}
```

### Bước 5: Cài đặt `unlockUser` tại `ManagerUserServiceImpl.java`
```java
@Transactional
@Override
public void unlockUser(UUID managerId, UnlockUserRequest req) {
    if (req.userId().equals(managerId))
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không thể tự mở khóa tài khoản của chính mình.");

    User user = getActiveUser(req.userId());

    int targetLevel = getLevel(req.userId());
    int managerLevel = getLevel(managerId);

    // Số nhỏ = quyền cao — không được mở khóa user có cấp bậc cao hơn hoặc bằng chính mình.
    if (targetLevel <= managerLevel) {
        throw new BusinessException(ErrorCode.FORBIDDEN, "Không được mở khóa tài khoản có cấp bậc cao hơn hoặc bằng chính mình.");
    }

    if (user.getStatus() != UserStatus.BLOCKED) {
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tài khoản không ở trạng thái bị khóa.");
    }

    user.setStatus(UserStatus.ACTIVE);
    userRepository.save(user);

    // Xóa blacklist trong Redis
    blacklistedUserRepository.deleteById(req.userId().toString());
    userAuthCacheHelper.evictUserAuth(user.getId());

    log.info("User {} đã mở khóa tài khoản user {}", managerId, req.userId());
}
```

### Bước 6: Thêm Endpoint tại `ManagerUserController.java`
```java
@PatchMapping("/unlock")
@PreAuthorize("hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).USERS_UPDATE.getValue())")
public ApiResponse<Void> unlockUser(
        @Valid @RequestBody UnlockUserRequest req) {
    adminUserService.unlockUser(SecurityContextUtil.currentUserId(), req);
    return ApiResponse.of(null, "Mở khóa tài khoản người dùng thành công");
}
```

---

## 3. Danh sách công việc chi tiết

1. **Cập nhật `AuthServiceImpl.java`:**
   - [ ] Đổi `req.email()` thành biến cục bộ `String email = req.email().toLowerCase();` trong `register()`.
   - [ ] Đổi `req.email()` thành `email` trong `verifyEmail()`.
   - [ ] Đổi `req.email()` thành `email` trong `resendVerification()`, xoá bỏ comment mâu thuẫn.
   - [ ] Đổi `req.email()` thành `email` trong `forgotPassword()`.
   - [ ] Đổi `req.email()` thành `email` trong `resetPassword()`.
2. **Cập nhật `EmailLoginImpl.java`:**
   - [ ] Bỏ `.trim()` ở dòng 39, chỉ giữ `req.email().toLowerCase()`.
3. **Cập nhật `UserServiceImpl.java`:**
   - [ ] Bổ sung kiểm tra `if (user.isLocked())` trong `updateStatus()` để ném `AUTH_ACCOUNT_BLOCKED`.
4. **Bổ sung tính năng Admin Unlock User:**
   - [ ] Tạo `UnlockUserRequest.java`.
   - [ ] Khai báo `unlockUser` trong `ManagerUserService.java`.
   - [ ] Cài đặt `unlockUser` trong `ManagerUserServiceImpl.java`.
   - [ ] Thêm API `PATCH /admin/user/unlock` trong `ManagerUserController.java`.
5. **Kiểm thử tự động:**
   - [ ] Viết test case cho email in hoa và chặn mở khoá trong `AuthEmailVerificationIntegrationTest.java`.
   - [ ] Viết test case Admin mở khoá tài khoản trong `AdminBlockUserIntegrationTest.java`.
   - [ ] Chạy kiểm thử toàn bộ các test liên quan:
     ```powershell
     mvn test -Dtest=AuthEmailVerificationIntegrationTest,AuthBlockedDeletedAccountIntegrationTest,AdminBlockUserIntegrationTest
     ```

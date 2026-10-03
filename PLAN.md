# Kế hoạch sửa lỗi Test sau khi Refactor module User & Auth

Sau khi phân tích logic cũ trong các file test bị lỗi và cấu trúc mới của module `user` / `auth`, tôi nhận thấy nguyên nhân chính là:
- `ProfileService` và `AccountService` đã bị xóa/gộp và thay thế bởi `UserBehavierService` (cho các logic như `getMe`, `updateMe`, `deleteMe`, `changePassword`) và `UserService` (cho các logic quản lý user chung, update status).
- Các Exception cụ thể của module auth (như `AuthResetCodeInvalidException`, `AuthPasswordSameAsOldException`) đã bị xóa và thay bằng `BusinessException(ErrorCode.XXX)`.
- Các dependencies của `AuthServiceImpl` đã thay đổi hoàn toàn.

Dưới đây là phương án sửa đổi chi tiết, đảm bảo không làm thay đổi logic nghiệp vụ hiện tại:

## 1. UserControllerTest.java
- **Vấn đề**: Đang mock `ProfileService` và `AccountService`.
- **Giải pháp**: 
  - Thay bằng `@Mock UserBehavierService userBehavierService`.
  - Cập nhật các phương thức `when(...)` và `verify(...)` (ví dụ: đổi `userProfileService.getMe` thành `userBehavierService.getMe`, đổi `userAccountService.generatePassword` thành `userBehavierService.createPassword`).

## 2. AuthResetPasswordServiceTest.java
- **Vấn đề**: Sử dụng dependency cũ của `AuthServiceImpl` và bắt lỗi các exception cũ không còn tồn tại.
- **Giải pháp**:
  - Sửa lại hàm `setUp()` để inject đúng các dependencies mới của `AuthServiceImpl` (`UserService`, `TokenService`, `EmailService`, `PasswordEncoder`, `ForgotPasswordRateLimiter`, `OtpRepository`).
  - Đổi các `@Mock AccountService` thành `@Mock UserService`.
  - Thay đổi điều kiện `assertThatThrownBy(...).isInstanceOf(...)` thành `BusinessException.class` và assert theo các mã lỗi đã chuẩn hóa mới: `ErrorCode.AUTH_RESET_CODE_INVALID` / `ErrorCode.AUTH_PASSWORD_SAME_AS_OLD`.
  - Cập nhật tương tự cho `UserControllerTest` nếu mock các mã lỗi như `AUTH_PASSWORD_ALREADY_SET`.

## 3. Các file Integration Test
Các file: `AuthBlockedDeletedAccountIntegrationTest`, `AuthGoogleLoginIntegrationTest`, `UserDeleteAccountIntegrationTest`, `UserProfileIntegrationTest`.
- **Vấn đề**: Đang `@Autowired` `ProfileService` hoặc `AccountService` để chuẩn bị dữ liệu hoặc verify kết quả.
- **Giải pháp**:
  - Gỡ bỏ `@Autowired ProfileService`, `@Autowired AccountService`.
  - Thay thế bằng `@Autowired UserBehavierService` hoặc `@Autowired UserService` tương ứng với logic đang dùng. (Ví dụ: `deleteMe` thì dùng `UserBehavierService`, còn nếu truy vấn lấy dữ liệu user ra thì dùng `UserService` hoặc dùng trực tiếp Repository).

## 4. AccountServiceImplTest.java
- **Vấn đề**: `AccountServiceImpl` không còn tồn tại, gây lỗi `cannot find symbol`.
- **Giải pháp**:
  - Tùy chọn A (Đề xuất): **Xóa file này** vì class tương ứng đã bị xóa/gộp. Nếu cần, có thể viết mới `UserBehavierServiceImplTest` / `UserServiceImplTest` sau.
  - Tùy chọn B: Đổi tên thành `UserBehavierServiceImplTest` và viết lại toàn bộ test theo logic mới của `UserBehavierService`.

---

**Bạn xem qua plan trên, đặc biệt là phần 4 (bạn muốn xóa `AccountServiceImplTest` hay viết lại?), nếu đồng ý hãy báo tôi để tôi bắt đầu tiến hành sửa code nhé.**

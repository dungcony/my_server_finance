# Kế hoạch sửa lỗi 500 khi đăng nhập sai email

## Nguyên nhân gốc (Root Cause)

1. Lỗi xảy ra do cơ chế quản lý Transaction của Spring (Spring Transaction).
2. Khi đăng nhập bằng email không tồn tại, hàm `userService.get()` bên trong `UserServiceImpl` sẽ ném ra lỗi `UserNotFoundException`.
3. Vì `UserServiceImpl` có đánh dấu `@Transactional` ở mức class, khi một `RuntimeException` (như `UserNotFoundException`) bay ra, Spring sẽ đánh dấu toàn bộ transaction chung là **rollback-only**.
4. Mặc dù ở ngoài `EmailLoginImpl.login()` có cấu hình `@Transactional(noRollbackFor = BusinessException.class)` để cố gắng bỏ qua lỗi và tiếp tục lưu lịch sử đăng nhập sai (`saveLoginAttemp`), nhưng vì transaction đã bị ruột bên trong (`UserServiceImpl`) đánh dấu là rollback-only từ trước, nên khi kết thúc hàm `login()`, Spring cố gắng commit transaction và gây ra lỗi `UnexpectedRollbackException`.
5. Ngoại lệ này bị văng ra tận Controller, làm cho API trả về HTTP 500 thay vì 401. 
6. Hơn nữa, vì lỗi không được catch bên trong `EmailLoginImpl`, code không bao giờ chạy đến đoạn `saveLoginAttemp`, dẫn đến việc không thể lưu lịch sử sai mật khẩu để bảo vệ hệ thống (chặn sau 5 lần sai).

## Đề xuất thay đổi

Thay đổi cần thực hiện ở 2 file:

**1. `UserServiceImpl.java`**
- Thêm annotation `@Transactional(readOnly = true, noRollbackFor = UserNotFoundException.class)` vào hàm `get(UserGetReq req)`.
- **Mục đích:** Báo cho Spring biết rằng nếu hàm này ném ra `UserNotFoundException`, đừng đánh dấu transaction là rollback-only. (Hàm get chỉ đọc dữ liệu nên không lo việc không rollback làm sai lệch dữ liệu).

**2. `EmailLoginImpl.java`**
- Bọc đoạn gọi `userService.get()` trong một khối `try-catch` để bắt lỗi `UserNotFoundException` (hoặc `BusinessException` có mã lỗi `NOT_FOUND`).
- **Mục đích:** Để khi không tìm thấy user, biến `user` sẽ bằng `null`, hàm sẽ tiếp tục chạy xuống dưới để thực hiện lưu lịch sử đăng nhập sai `saveLoginAttemp(email, false, ...)` thay vì bị crash giữa chừng.
- Cần thêm import `UserRes` do phải đổi từ `var user = ...` sang khai báo tường minh.

## Xin xác nhận

Bạn vui lòng xem kỹ nguyên nhân và giải pháp đề xuất. Nếu bạn đồng ý, hãy phản hồi lại hoặc bấm/gõ "Process" / "Đồng ý" để tôi bắt đầu sửa code nhé.

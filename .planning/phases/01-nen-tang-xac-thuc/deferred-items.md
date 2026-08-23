# Deferred Items — Phase 01 nen-tang-xac-thuc

## [ĐÃ SỬA] `JwtServiceTest.tamperedSignature_throwsSignatureException` không ổn định (flaky) khi chạy cùng full suite

- **Phát hiện trong:** Plan 04, khi chạy `mvn test` toàn bộ project để xác nhận không có regression
- **File:** `src/test/java/com/datn/financeapp/common/security/JwtServiceTest.java` (tạo ở Plan 02, không thuộc phạm vi sửa của Plan 04)
- **Hiện tượng:** Test PASS khi chạy riêng (`mvn test -Dtest=JwtServiceTest`), nhưng FAIL khi chạy cùng toàn bộ suite (`mvn test`) — lỗi `Expected SignatureException to be thrown, but nothing was thrown`. Test sửa 1 ký tự cuối cùng của JWT để giả lập chữ ký bị sửa; xác suất nhỏ ký tự bị đổi không phá vỡ phần padding base64url của chữ ký (không ảnh hưởng tới giá trị giải mã), khiến signature verify vẫn qua dù chuỗi token đã đổi.
- **Trạng thái:** Đã sửa ở Plan 06 — thay đổi ký tự cuối chuỗi base64url (rủi ro rơi vào padding) bằng cách giải mã signature segment thành mảng byte thật, đảo bit (`^= 0xFF`) của byte Ở GIỮA mảng rồi mã hoá lại. Cách này không thể là no-op bất kể vị trí padding.
- **Verify:** 10 lần chạy liên tục `mvn test -Dtest=JwtServiceTest` (riêng lẻ) đều PASS; `mvn test` toàn bộ suite (34 test) cũng PASS.
- **Commit sửa:** xem `01-06-SUMMARY.md`.


## Giải IP client không nhất quán giữa `ClientIpResolver` và `RateLimitFilter`

- **Phát hiện trong:** code review Phase 01 (`01-REVIEW.md`, Warning #1)
- **Quyết định:** người dùng chọn HOÃN sang phase deployment — lúc đó mới biết proxy nào thật sự
  đứng trước, nên cấu hình allowlist bây giờ sẽ là phỏng đoán.
- **File:** `src/main/java/com/datn/financeapp/common/security/ClientIpResolver.java`,
  `src/main/java/com/datn/financeapp/common/ratelimit/RateLimitFilter.java:48`

**Hai vấn đề ngược chiều nhau, không tự triệt tiêu:**

1. `ClientIpResolver.resolve()` tin `X-Forwarded-For` VÔ ĐIỀU KIỆN, không có allowlist proxy tin
   cậy. Client tự gửi header này là ghi được IP giả vào `login_attempts.ip_address` — làm nhiễu
   nhật ký điều tra, và về lý thuyết né được khoá đăng nhập theo IP (AUTH-07).
2. `RateLimitFilter` KHÔNG dùng `ClientIpResolver` mà gọi thẳng `req.getRemoteAddr()`, dù Javadoc
   của `ClientIpResolver` ghi rõ nó phục vụ CORE-04. Hiện tại điều này vô tình an toàn (không giả
   IP để né rate limit được), NHƯNG khi deploy sau reverse proxy thật thì mọi client sẽ trông
   giống hệt một IP — rate limit theo IP mất tác dụng hoàn toàn.

**Cách khắc phục khi tới phase deployment:**
- Thêm cấu hình danh sách proxy tin cậy (Spring Boot có sẵn `server.forward-headers-strategy`, hoặc
  tự kiểm tra `getRemoteAddr()` có nằm trong allowlist trước khi đọc `X-Forwarded-For`).
- Cho `RateLimitFilter` dùng chung `ClientIpResolver` để hai chỗ nhất quán.
- Bổ sung test: client gửi `X-Forwarded-For` giả từ IP ngoài allowlist thì header phải bị bỏ qua.

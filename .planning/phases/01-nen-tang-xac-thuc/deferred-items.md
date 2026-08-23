# Deferred Items — Phase 01 nen-tang-xac-thuc

## [ĐÃ SỬA] `JwtServiceTest.tamperedSignature_throwsSignatureException` không ổn định (flaky) khi chạy cùng full suite

- **Phát hiện trong:** Plan 04, khi chạy `mvn test` toàn bộ project để xác nhận không có regression
- **File:** `src/test/java/com/datn/financeapp/common/security/JwtServiceTest.java` (tạo ở Plan 02, không thuộc phạm vi sửa của Plan 04)
- **Hiện tượng:** Test PASS khi chạy riêng (`mvn test -Dtest=JwtServiceTest`), nhưng FAIL khi chạy cùng toàn bộ suite (`mvn test`) — lỗi `Expected SignatureException to be thrown, but nothing was thrown`. Test sửa 1 ký tự cuối cùng của JWT để giả lập chữ ký bị sửa; xác suất nhỏ ký tự bị đổi không phá vỡ phần padding base64url của chữ ký (không ảnh hưởng tới giá trị giải mã), khiến signature verify vẫn qua dù chuỗi token đã đổi.
- **Trạng thái:** Đã sửa ở Plan 06 — thay đổi ký tự cuối chuỗi base64url (rủi ro rơi vào padding) bằng cách giải mã signature segment thành mảng byte thật, đảo bit (`^= 0xFF`) của byte Ở GIỮA mảng rồi mã hoá lại. Cách này không thể là no-op bất kể vị trí padding.
- **Verify:** 10 lần chạy liên tục `mvn test -Dtest=JwtServiceTest` (riêng lẻ) đều PASS; `mvn test` toàn bộ suite (34 test) cũng PASS.
- **Commit sửa:** xem `01-06-SUMMARY.md`.

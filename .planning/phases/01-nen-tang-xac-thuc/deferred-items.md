# Deferred Items — Phase 01 nen-tang-xac-thuc

## `JwtServiceTest.tokenBiSuaChuKy_nemSignatureException` không ổn định (flaky) khi chạy cùng full suite

- **Phát hiện trong:** Plan 04, khi chạy `mvn test` toàn bộ project để xác nhận không có regression
- **File:** `src/test/java/com/datn/financeapp/common/security/JwtServiceTest.java` (tạo ở Plan 02, không thuộc phạm vi sửa của Plan 04)
- **Hiện tượng:** Test PASS khi chạy riêng (`mvn test -Dtest=JwtServiceTest`), nhưng FAIL khi chạy cùng toàn bộ suite (`mvn test`) — lỗi `Expected SignatureException to be thrown, but nothing was thrown`. Test sửa 1 ký tự cuối cùng của JWT để giả lập chữ ký bị sửa; xác suất nhỏ ký tự bị đổi không phá vỡ phần padding base64url của chữ ký (không ảnh hưởng tới giá trị giải mã), khiến signature verify vẫn qua dù chuỗi token đã đổi.
- **Ngoài phạm vi Plan 04:** File thuộc Plan 02, không được sửa hay tạo lại bởi Plan 04. Theo scope boundary, không tự sửa ở đây.
- **Đề xuất khắc phục (cho phase sau hoặc lần review Plan 02):** đổi ký tự ở vị trí giữa payload/signature thay vì luôn ký tự cuối, hoặc lặp thử vài ký tự khác nhau tới khi tìm được ký tự chắc chắn phá signature.

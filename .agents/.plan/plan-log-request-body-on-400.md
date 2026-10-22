# Log body request khi server trả 400

## Bối cảnh

- `POST /v1/recurring` trả 400 `HttpMessageNotReadableException: ... expecting comma` nhưng log không cho biết body server nhận được (`RequestLoggingFilter.java` chỉ log method/path/status). Body đã bị Jackson đọc xong nên không đọc lại được trong `GlobalExceptionHandler`.

## Quyết định đã chốt

- Chưa có (xem "Câu hỏi còn mở").

## 1. Các file sẽ thay đổi

**Code**

- `src/main/java/com/datn/financeapp/common/logging/RequestLoggingFilter.java`: bọc request bằng `ContentCachingRequestWrapper`; khi `status == 400` thì log thêm body đã cache (cắt tối đa 2000 ký tự).
- `src/main/java/com/datn/financeapp/common/logging/LogMasker.java:31`: `mask(String)` đang `static` package-private, cùng package nên dùng được để che `password`, token, OTP trước khi log (không cần sửa).

Không đổi: `GlobalExceptionHandler.java`, API contract, DB.

## 2. Lý do thay đổi

- Cần thấy JSON thật server nhận để debug lỗi parse; rule 2.10: phải mask dữ liệu nhạy cảm trước khi log.

## 3. Hướng giải quyết / code xem trước

```java
HttpServletRequest req = new ContentCachingRequestWrapper(request);
chain.doFilter(req, response);
...
// trong finally, nếu status == 400:
log.warn("Request body (400): {}", LogMasker.mask(readBody(req)));
```

- `readBody`: lấy `getContentAsByteArray()`, decode UTF-8, cắt 2000 ký tự.
- ⚠️ Chỉ log khi 400 (không log mọi request) để tránh lộ dữ liệu và tốn log.
- Không thêm dependency.

## 4. Danh sách công việc chi tiết

1. Bọc request bằng `ContentCachingRequestWrapper` trong `doFilterInternal`.
2. Thêm hàm private `readBody` và gọi trong `finally` khi status 400.
3. Mask bằng `LogMasker.mask`.

## Ngoài phạm vi

- Không log body cho các status khác; không đổi `GlobalExceptionHandler`.

## Kiểm tra

- Có viết test không? (hỏi bên dưới). Lệnh nếu có test: `mvn test -Dtest=RequestLoggingFilterTest` (chưa kiểm tra lớp này tồn tại).
- Bạn tự gửi lại request lỗi và xem log.

## Câu hỏi còn mở

1. Có giữ log này lâu dài, hay chỉ tạm để debug rồi gỡ?
2. Có viết test cho filter không?

## Bảng công việc

| # | Việc | Trạng thái |
|---|---|---|
| 1 | Bọc request | hoàn thành |
| 2 | Hàm readBody + log khi 400 | hoàn thành |
| 3 | Mask | hoàn thành |

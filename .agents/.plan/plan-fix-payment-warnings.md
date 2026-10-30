# Sửa cảnh báo IDE và tối ưu hàm tính tiền PaymentServiceImpl

## Bối cảnh

Trong file `PaymentServiceImpl.java`, IDE (IntelliJ IDEA) phát hiện 2 cảnh báo vàng (Warnings) tại phương thức `receivedOf(PaymentLink link)`:
- Dòng 344: `Condition 'link.getTransactions() == null' is always 'false'`
- Dòng 347: `Condition 'link.getAmountPaid() == null' is always 'false'`

Ngoài ra, phương thức này đang mang tiêu đề chú thích `PHƯƠNG THỨC HỖ TRỢ TEST` và phạm vi truy cập `package-private`, trong khi thực tế đây là logic nghiệp vụ chính (production logic). Đồng thời, chú thích ở kiểm tra trạng thái đơn cũ cần được tách rõ cho hai nhánh `PAID` và `PARTIALLY_PAID`.

## Quyết định đã chốt

- Bỏ kiểm tra `== null` dư thừa trên `link.getTransactions()` và `link.getAmountPaid()` vì thư viện SDK `payos-java` đã có `@NonNull`.
- Đổi phạm vi của `receivedOf` từ package-private (`static long`) sang `private static long` và gom vào khối các phương thức nội bộ private.
- Xóa bỏ tiêu đề phân đoạn `PHƯƠNG THỨC HỖ TRỢ TEST`.
- Tách riêng câu chú thích tại dòng kiểm tra đơn cũ: nhánh `PAID` (đã thanh toán đủ) và nhánh `PARTIALLY_PAID` (thanh toán thiếu).

## 1. Các file sẽ thay đổi

**Code:**
- `src/main/java/com/datn/financeapp/module/pay/service/impl/PaymentServiceImpl.java:114-119` (tách chú thích rõ ràng cho từng nhánh `PAID` và `PARTIALLY_PAID`).
- `src/main/java/com/datn/financeapp/module/pay/service/impl/PaymentServiceImpl.java:339-350` (chuẩn hóa `receivedOf`, xóa tiêu đề hỗ trợ test, bỏ kiểm tra null dư thừa).

**File không đổi:**
- Toàn bộ các file test trong `src/test/java/...` giữ nguyên (không bị ảnh hưởng vì test mock ở tầng `PayOsClientHelper` và gọi public API).

## 2. Lý do thay đổi

- SDK payOS (`vn.payos.model.v2.paymentRequests.PaymentLink`) đã đánh dấu `@NonNull` ở hai trường `amountPaid` và `transactions`. IDE nhận biết các trường này không null nên cảnh báo điều kiện so sánh `== null` luôn false.
- Hàm `receivedOf` là logic nghiệp vụ chạy thật trong hệ thống, không phải phương thức phụ trợ dành riêng cho test, do đó cần đặt phạm vi `private` để đảm bảo tính đóng gói (encapsulation).

## 3. Hướng giải quyết / code xem trước

### Đoạn 1: Tách rõ chú thích kiểm tra đơn cũ (`PaymentServiceImpl.java:114-120`)

```java
        // nếu đơn cũ đã được thanh toán đủ thì trả về kết quả thành công, không tạo đơn mới
        if (active.getStatus() == PaymentStatus.PAID)
            return paymentMapper.toCreateRes(active, null);

        // nếu đơn cũ thanh toán thiếu tiền thì chặn tạo mới, yêu cầu nạp bù
        if (active.getStatus() == PaymentStatus.PARTIALLY_PAID)
            throw new BusinessException(ErrorCode.PAYMENT_PARTIALLY_PAID_PENDING);
```

### Đoạn 2: Chuẩn hóa hàm `receivedOf` (`PaymentServiceImpl.java:339-350`)

Xóa tiêu đề `// -------------------------------- PHƯƠNG THỨC HỖ TRỢ TEST --------------------------------` và chuyển thành:

```java
    // tính tổng số tiền thực tế đã nhận qua link thanh toán
    private static long receivedOf(PaymentLink link) {
        if (link == null) return 0L;
        // tính tổng các giao dịch thành công đã nhận qua link
        long sum = link.getTransactions().stream()
                .mapToLong(vn.payos.model.v2.paymentRequests.Transaction::getAmount).sum();
        return Math.max(sum, link.getAmountPaid());
    }
```

## 4. Danh sách công việc chi tiết

- Chỉnh sửa `PaymentServiceImpl.java`:
  - Cập nhật chú thích tại dòng 114-119.
  - Xóa dòng tiêu đề phân đoạn hỗ trợ test.
  - Sửa `receivedOf` thành `private static long` và bỏ check null dư thừa.
- Biên dịch thử nghiệm để đảm bảo cú pháp chính xác: `mvn test-compile -DskipTests`.

## Ngoài phạm vi

- Không thay đổi các phương thức nghiệp vụ khác của `PaymentServiceImpl`.
- Không thay đổi database hay cấu hình.

## Kiểm tra

- Biên dịch dự án:
  ```bash
  mvn test-compile -DskipTests
  ```
- Chạy unit test kiểm tra module thanh toán (nếu người dùng yêu cầu):
  ```bash
  mvn test -Dtest=PaymentServiceImplPartialTest,PaymentServiceImplTest
  ```
- *Lưu ý quy tắc*: Không tự ý chạy test nếu chưa có yêu cầu từ người dùng.

## Câu hỏi còn mở

- Hiện tại không còn câu hỏi mở.

## Bảng công việc

| Công việc | Trạng thái |
| --- | --- |
| Chuẩn hóa `receivedOf` và xóa cảnh báo null trong `PaymentServiceImpl.java` | Hoàn thành |
| Cập nhật chú thích rõ ràng cho nhánh `PAID` và `PARTIALLY_PAID` | Hoàn thành |
| Kiểm tra biên dịch `mvn test-compile -DskipTests` | Hoàn thành |

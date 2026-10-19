# Kế hoạch Giai đoạn 2: Xử lý thanh toán thiếu tiền (`PARTIALLY_PAID`), quá hạn bù (`FORFEITED`) & Bổ sung Comment giải thích code Module `pay`

## 1. Bối cảnh & Phát hiện thực tế từ payOS

Từ thử nghiệm thực tế với đơn hàng `200003`:
- **Chuyển thiếu (3.000đ / 4.000đ):** payOS **tự động trừ tiền và cập nhật số tiền còn thiếu (1.000đ)** trực tiếp trên mã QR và giao diện web thanh toán hiện tại; giữ nguyên nội dung chuyển khoản và mã đơn.
- **Chuyển bù tiếp (chuyển thêm 3.000đ thành 6.000đ / 4.000đ):** payOS tự động cộng dồn, chuyển trạng thái đơn sang **"Đã thanh toán" (`PAID`)**, số tiền thanh toán ghi nhận đủ 6.000đ.
- **Webhook & Polling:**
  - payOS **không** bắn webhook khi chuyển thiếu tiền (chỉ bắn khi thanh toán đủ `PAID`).
  - Server chủ động phát hiện đơn thiếu tiền qua:
    - Polling từ App (`GET /payments/{order_code}`): gọi `getLink` payOS cập nhật ngay lập tức.
    - Job nền định kỳ (`PaymentSyncJob` chạy mỗi 1 phút): quét các đơn `PENDING` còn hạn để đồng bộ.
- **Kết luận kiến trúc:**
  - **Không cần bảng `payment_links` phụ.**
  - **Không cần endpoint tạo link bù mới `POST /payments/{order_code}/topup-links`.**
  - Khách chỉ cần mở lại đúng `checkout_url` ban đầu hoặc quét lại QR ban đầu là payOS tự hiển thị số tiền còn thiếu để thanh toán tiếp.

---

## 2. Các quyết định thiết kế cho Giai đoạn 2

| # | Nội dung | Trạng thái |
|---|---|---|
| 1 | Thêm 2 trạng thái mới vào `PaymentStatus`: `PARTIALLY_PAID` (đã nhận một phần tiền, đang chờ bù) và `FORFEITED` (quá hạn bù, đóng đơn và giữ tiền). | Hoàn thành |
| 2 | Migration `V19__payment_partially_paid.sql`: Mở constraint `ck_payments_status`, thêm 3 cột vào bảng `payments`: `topup_deadline`, `topup_reminded_at`, `forfeited_at`. Cập nhật partial unique index: một user chỉ có tối đa 1 đơn đang mở (`PENDING` hoặc `PARTIALLY_PAID`) cho mỗi `type`. | Hoàn thành |
| 3 | Khi phát hiện qua polling hoặc sync job mà `0 < amount_paid < amount`: chuyển trạng thái sang `PARTIALLY_PAID`, lưu `amount_paid`, thiết lập `topup_deadline = now + PAYMENT_TOPUP_SECONDS`, phát sự kiện `PaymentPartiallyPaidEvent`. | Hoàn thành |
| 4 | Khi khách chuyển nốt phần còn thiếu (`amount_paid >= amount`): chuyển sang `PAID`, phát `PaymentPaidEvent` (kích hoạt Premium bình thường), lưu `amount_paid` (và tính `excess_amount` nếu trả thừa theo logic Giai đoạn 1). | Hoàn thành |
| 5 | Quá hạn bù tiền (`topup_deadline`): Job `PaymentTopupExpiryJob` gọi payOS `cancelLink` (khóa cổng nạp), chuyển trạng thái đơn thành `FORFEITED`, ghi nhận `forfeited_at = now`, phát `PaymentForfeitedEvent`, log `ERROR [PAYMENT-RECONCILE]`. Số tiền đã nhận không hoàn lại (theo yêu cầu). | Hoàn thành |
| 6 | Nhắc hạn bù: Khi thời gian còn lại `<= PAYMENT_TOPUP_REMINDER_SECONDS` và chưa nhắc (`topup_reminded_at IS NULL`), job phát `PaymentTopupReminderEvent` và cập nhật `topup_reminded_at = now`. | Hoàn thành |
| 7 | Module Thông báo (`notification`): Lắng nghe 3 event trên để tạo thông báo in-app gửi đến user với các nội dung rõ ràng về số tiền đã nhận, số tiền còn thiếu, thời hạn bù, và cảnh báo không hoàn tiền. | Hoàn thành |
| 8 | Cấu hình qua `.env`: `PAYMENT_TOPUP_SECONDS` (bắt buộc, ví dụ 86400 cho prod, 30 khi test), `PAYMENT_TOPUP_REMINDER_SECONDS` (mặc định 3600), `PAYMENT_TOPUP_JOB_SECONDS` (mặc định 60). | Hoàn thành |
| 9 | **Quy tắc Comment Code:** Bổ sung comment dòng (`//`) giải thích rõ ràng từng khối logic, quyết định nghiệp vụ, điều kiện rẽ nhánh trong cả code mới và toàn bộ code hiện tại của module `pay`. Tuân thủ tuyệt đối quy tắc: **không đánh số thứ tự trong comment** (chỉ dùng `// giải thích ý nghĩa logic`). | Hoàn thành |

---

## 3. Các bước triển khai & Trạng thái

| # | Bước thực hiện | Trạng thái |
|---|---|---|
| 1 | Tạo migration `V19__payment_partially_paid.sql` | Hoàn thành |
| 2 | Bổ sung enum, fields entity, DTOs và mapper | Hoàn thành |
| 3 | Bổ sung cấu hình `PaymentTopupProperties` và cập nhật `application.yml`, `application-test.yml`, `.env.example` | Hoàn thành |
| 4 | Rà soát, thêm comment chi tiết cho code hiện có trong module `pay` và triển khai logic `PARTIALLY_PAID`, `FORFEITED`, `PaymentSyncJob`, `PaymentTopupExpiryJob` | Hoàn thành |
| 5 | Tạo các sự kiện (Events) và listener thông báo trong module `notification` | Hoàn thành |
| 6 | Viết bộ test kiểm thử (Unit test cho Service, Job, Notification Listener) | Hoàn thành |
| 7 | Cập nhật tài liệu API `docs/api/payment.md` | Hoàn thành |
| 8 | Biên dịch dự án `mvn test-compile -DskipTests` (BUILD SUCCESS) | Hoàn thành |

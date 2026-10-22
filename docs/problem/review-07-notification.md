# Lỗi module Notification

[← Tổng quan](review-00-tong-quan.md)

## Notification

<a id="n1"></a>

### N1. Danh sách thông báo luôn rỗng — Nặng (Đã khắc phục)

- **Ở đâu:** [NotificationServiceImpl.java:46](../../src/main/java/com/datn/financeapp/module/notification/service/impl/NotificationServiceImpl.java#L46), [NotificationRepository.java:24](../../src/main/java/com/datn/financeapp/module/notification/repository/NotificationRepository.java#L24)
- **Chuyện gì xảy ra:** App gọi `GET /notifications` không kèm `?is_read=` (muốn lấy tất cả). Spring Data đổi
  `isRead = null` thành điều kiện `is_read IS NULL`. Cột này luôn có giá trị, nên **luôn trả về rỗng**.
- **Đã sửa:** Bổ sung phương thức `findAllByUserId` trong `NotificationRepository`. Trong `NotificationServiceImpl.list`, khi `isRead == null` gọi `findAllByUserId`, khi có giá trị mới lọc theo `findAllByUserIdAndIsRead`.

<a id="n2"></a>

### N2. Thông báo giao dịch nhóm gửi lỗi / sai nội dung — Vừa (Đã khắc phục)

- **Ở đâu:** [GroupTxnListener.java](../../src/main/java/com/datn/financeapp/module/notification/events/listener/GroupTxnListener.java)
- **Chuyện gì xảy ra & Đã sửa:**

| Vấn đề trước đây | Đã sửa |
|---|---|
| Gọi `event.categoryId().toString()` bị NPE khi không có danh mục | Kiểm tra null an toàn và chỉ đưa vào data nếu có |
| Gửi cho `transactorId` dẫn đến `null` khi chi từ quỹ hoặc hoàn tiền | Chi tiêu từ quỹ (`FUND`) không gửi ghi sổ cá nhân (đúng Rule 4). Hoàn tiền (`REFUND`) gửi riêng cho từng người được hoàn trong `participantShares` |
| `onPending` in chuỗi UUID người tạo | Lấy tên hiển thị của người tạo qua `UserService.getNames()` |
| `onPending` dùng nhầm type `GROUP_TXN_RECORD_REQUEST` và tiêu đề "Ghi vào sổ cá nhân?" | Đổi thành type `NotiType.GROUP_TXN_REVIEW_REQUEST` và tiêu đề "Giao dịch nhóm chờ duyệt" |

<a id="n3"></a>

### N3. API danh sách thông báo không bọc `ApiResponse` — Nhẹ (Đã khắc phục)

- **Ở đâu:** [NotificationController.java:22](../../src/main/java/com/datn/financeapp/module/notification/controller/NotificationController.java#L22)
- **Chuyện gì xảy ra:** Hàm `list` trả thẳng `NotificationListResponse`, không có `{ success, data, msg }` như các API khác (sai rule 2.4).
- **Đã sửa:** Chuẩn hóa `NotificationListResponse(items, meta)` và bọc `ApiResponse.of(...)` tại `NotificationController.list()`.

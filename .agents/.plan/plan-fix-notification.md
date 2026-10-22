# Sửa toàn bộ lỗi phân hệ Notification (N1, N2, N3) & Chuẩn hóa Event-driven cho Goal & Budget

## Bối cảnh

Tài liệu [review-07-notification.md](file:///D:/projects/java/server/docs/problem/review-07-notification.md) rà soát mã nguồn phân hệ Notification và chỉ ra 3 lỗi:
- **N1 (Mức Nặng):** Danh sách thông báo luôn rỗng khi gọi `GET /notifications` không kèm param `?is_read=`. Lý do: [NotificationServiceImpl.java:46](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/notification/service/impl/NotificationServiceImpl.java#L46) truyền `isRead = null` vào [NotificationRepository.java:24](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/notification/repository/NotificationRepository.java#L24) (`findAllByUserIdAndIsRead`), Spring Data sinh câu lệnh `is_read IS NULL`, nhưng cột này trong DB luôn có giá trị (`true`/`false`) nên kết quả luôn rỗng.
- **N2 (Mức Vừa):** Thông báo giao dịch nhóm trong [GroupTxnListener.java](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/notification/events/listener/GroupTxnListener.java) gặp hàng loạt vấn đề:
  - Dòng 44: Lỗi NPE khi giao dịch không có danh mục (người dùng đã sửa kiểm tra null ở working tree).
  - Dòng 50: Gửi thông báo cho `event.transactorId()`. Với chi tiêu dùng tiền quỹ (`MoneySource.FUND`) hoặc hoàn tiền (`REFUND`), `transactorId` là `null`, dẫn đến vi phạm ràng buộc DB `notifications.user_id NOT NULL` và thông báo bị hủy.
  - Giao dịch hoàn tiền (`REFUND`) tiền trả về cho từng người, đúng thiết kế phải gửi thông báo cho từng người được hoàn tiền theo phần nhận của họ.
  - Giao dịch chi tiêu bằng quỹ chung (`MoneySource.FUND`) không phải tiền túi nên theo [rule.md:Rule 4](file:///D:/projects/java/server/docs/design/group/rule.md) và [api.md](file:///D:/projects/java/server/docs/design/group/api.md#333d-record-request) không được gửi thông báo ghi sổ cá nhân.
  - Dòng 66: Nội dung thông báo `onPending` in `UUID` người tạo thay vì tên hiển thị.
  - Dòng 74-75: Thông báo chờ duyệt dùng sai loại (`GROUP_TXN_RECORD_REQUEST`) và tiêu đề (`"Ghi vào sổ cá nhân?"`) thay vì `GROUP_TXN_REVIEW_REQUEST` và tiêu đề duyệt.
- **N3 (Mức Nhẹ):** [NotificationController.java:22](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/notification/controller/NotificationController.java#L22) trả thẳng `NotificationListResponse` thay vì bọc chuẩn `ApiResponse<...>` theo quy ước hệ thống (Rule 2.4 trong [spring-boot_struct.md](file:///D:/projects/java/server/.agents/spring-boot_struct.md)).
- **Mở rộng kiến trúc (Goal & Budget Notification):**
  - Module `goal`: Khi nạp tiền đạt mốc hoàn thành mục tiêu (`status == completed`), chưa có cơ chế sinh thông báo `GOAL_COMPLETED`.
  - Module `budget`: `BudgetSpendImpl` đang gọi trực tiếp đồng bộ `NotificationService.createBudgetAlert` làm nghẽn luồng tạo giao dịch chi tiêu cá nhân. Chuyển sang mô hình Event-driven bất đồng bộ `@Async` `@TransactionalEventListener(AFTER_COMMIT)`.

## Quyết định đã chốt

1. **Chuẩn hóa API `GET /notifications` (N3):**
   - Đổi `NotificationListResponse` thành `public record NotificationListResponse(List<NotificationRes> items, PageMeta meta)`.
   - Bọc `ApiResponse.of(...)` tại `NotificationController.list()`. Kết quả JSON trả về chuẩn Rule 2.4:
     ```json
     {
       "success": true,
       "data": {
         "items": [...],
         "meta": { "page": 1, "page_size": 20, "total_items": 10, "total_pages": 1 }
       },
       "msg": ""
     }
     ```
2. **Xử lý lọc thông báo khi `is_read` null (N1):**
   - Bổ sung `Page<Notification> findAllByUserId(UUID currentUser, Pageable pageable);` vào `NotificationRepository`.
   - Trong `NotificationServiceImpl.list`: Nếu `isRead == null` gọi `findAllByUserId`, nếu có giá trị mới gọi `findAllByUserIdAndIsRead`.
3. **Chuẩn hóa logic gửi thông báo giao dịch nhóm (N2):**
   - **`CONTRIBUTION`**: Gửi cho `event.transactorId()`, số tiền `event.amount()`, loại chi tiêu `EXPENSE`, danh mục `gop_quy_nhom` (hoặc `categoryCode` nếu có).
   - **`EXPENSE`**:
     - Nếu `event.transactorId() == null` (chi tiêu từ tiền quỹ `MoneySource.FUND`): **Không gửi thông báo ghi sổ cá nhân**.
     - Nếu `event.transactorId() != null` (chi tiêu bằng tiền túi `PERSONAL`): Gửi cho `event.transactorId()`, số tiền `event.amount()`, loại `EXPENSE`, danh mục `event.categoryCode()`.
   - **`REFUND`**: Duyệt `event.participantShares()`, gửi cho **từng thành viên nhận tiền** (`userId`) với số tiền là `shareAmount` của người đó, loại thu nhập `INCOME`, danh mục `nhan_hoan_tien_nhom`.
   - **`onPending`**:
     - Inject `UserService` vào `GroupTxnListener`, lấy tên người tạo qua `userService.getNames(List.of(event.createdBy()))` để in tên vào thông báo.
     - Đổi type thành `NotiType.GROUP_TXN_REVIEW_REQUEST`.
     - Đổi tiêu đề thành `"Giao dịch nhóm chờ duyệt"`.
4. **Kiến trúc Event-driven cho Goal (`GOAL_COMPLETED`):**
   - Tạo event `GoalCompletedEvent(UUID userId, UUID goalId, String goalName, long targetAmount)`.
   - `GoalServiceImpl.addContribution`: Khi mục tiêu chuyển sang `completed`, phát `GoalCompletedEvent` qua `ApplicationEventPublisher`.
   - Tạo listener `GoalCompletedListener` trong module `notification`: Bắt sự kiện `@TransactionalEventListener(AFTER_COMMIT)` và `@Async`, gọi `notificationService.createNotification(..., NotiType.GOAL_COMPLETED, ...)`.
5. **Kiến trúc Event-driven cho Budget (`BUDGET_ALERT`):**
   - Tạo event `BudgetAlertEvent(UUID userId, String title, String content, UUID budgetId)`.
   - `BudgetSpendImpl`: Thay thế dependency `NotificationService` bằng `ApplicationEventPublisher`. Khi `needsAttention == true`, phát `BudgetAlertEvent`.
   - Tạo listener `BudgetAlertListener` trong module `notification`: Bắt sự kiện `@TransactionalEventListener(AFTER_COMMIT)` và `@Async`, gọi `notificationRepository.insertBudgetAlertIfNotExists(...)` (chống trùng lặp theo `uq_notif_budget_alert`).
6. **Viết và cập nhật kiểm thử:**
   - Cập nhật `NotificationIntegrationTest` theo format response mới và thêm test case kiểm tra `is_read = null`.
   - Viết Unit Test `GroupTxnListenerTest`, `GoalCompletedListenerTest`.
   - Cập nhật `BudgetSpendImplTest` với `ApplicationEventPublisher`.

---

## 1. Các file đã thay đổi

### Code
- `src/main/java/com/datn/financeapp/module/notification/repository/NotificationRepository.java`:
  - Thêm phương thức `Page<Notification> findAllByUserId(UUID currentUser, Pageable pageable);`.
- `src/main/java/com/datn/financeapp/module/notification/dto/response/NotificationListResponse.java`:
  - Cập nhật record thành `public record NotificationListResponse(List<NotificationRes> items, PageMeta meta)`.
- `src/main/java/com/datn/financeapp/module/notification/service/impl/NotificationServiceImpl.java`:
  - Cập nhật hàm `list`: rẽ nhánh `isRead == null` và đóng gói `NotificationListResponse.of(items, meta)`.
- `src/main/java/com/datn/financeapp/module/notification/controller/NotificationController.java`:
  - Cập nhật hàm `list`: trả về kiểu `ApiResponse<NotificationListResponse>`.
- `src/main/java/com/datn/financeapp/module/notification/events/listener/GroupTxnListener.java`:
  - Tiêm `UserService` để lấy tên hiển thị của người tạo giao dịch.
  - Sửa `onConfirmed`: xử lý riêng cho `REFUND` (gửi từng người), `EXPENSE` cá nhân (bỏ qua nếu chi từ tiền quỹ), và `CONTRIBUTION`.
  - Sửa `onPending`: hiển thị tên người tạo, dùng `NotiType.GROUP_TXN_REVIEW_REQUEST`, tiêu đề `"Giao dịch nhóm chờ duyệt"`.

### Bổ sung mới cho Event Goal & Budget
- `src/main/java/com/datn/financeapp/module/goal/event/GoalCompletedEvent.java` (File mới):
  - Record chứa dữ liệu sự kiện: `userId, goalId, goalName, targetAmount`.
- `src/main/java/com/datn/financeapp/module/goal/service/impl/GoalServiceImpl.java`:
  - Inject `ApplicationEventPublisher`, phát `GoalCompletedEvent` khi `refreshedStatus == completed`.
- `src/main/java/com/datn/financeapp/module/notification/events/listener/GoalCompletedListener.java` (File mới):
  - Lắng nghe `GoalCompletedEvent` `@Async`, tạo thông báo `NotiType.GOAL_COMPLETED`.
- `src/main/java/com/datn/financeapp/module/budget/events/BudgetAlertEvent.java` (File mới):
  - Record chứa dữ liệu cảnh báo ngân sách: `userId, title, content, budgetId`.
- `src/main/java/com/datn/financeapp/module/budget/service/impl/BudgetSpendImpl.java`:
  - Đổi từ `NotificationService` sang `ApplicationEventPublisher.publishEvent(new BudgetAlertEvent(...))`.
- `src/main/java/com/datn/financeapp/module/notification/events/listener/BudgetAlertListener.java` (File mới):
  - Lắng nghe `BudgetAlertEvent` `@Async`, gọi `notificationRepository.insertBudgetAlertIfNotExists(...)`.

### Test
- `src/test/java/com/datn/financeapp/module/notification/controller/NotificationIntegrationTest.java`:
  - Cập nhật đường dẫn jsonPath từ `$.data` thành `$.data.items` và `$.pagination` thành `$.data.meta`.
  - Bổ sung test case `getNotifications_withoutIsReadParam_returnsAllNotifications`.
- `src/test/java/com/datn/financeapp/module/notification/events/listener/GroupTxnListenerTest.java` (File mới):
  - Unit test kiểm tra các nhánh `onConfirmed` và `onPending` của `GroupTxnListener`.
- `src/test/java/com/datn/financeapp/module/notification/events/listener/GoalCompletedListenerTest.java` (File mới):
  - Unit test cho `GoalCompletedListener`.
- `src/test/java/com/datn/financeapp/module/budget/service/impl/BudgetSpendImplTest.java`:
  - Cập nhật mock `ApplicationEventPublisher` thay cho `NotificationService`.
- `src/test/java/com/datn/financeapp/module/notification/events/listener/BudgetAlertListenerTest.java` (File mới):
  - Unit test cho `BudgetAlertListener`.

### Tài liệu
- `docs/problem/review-07-notification.md`:
  - Cập nhật trạng thái đã giải quyết cho N1, N2, N3.

---

## 2. Lý do thay đổi

- **N1 (NotificationServiceImpl.java:46):** `findAllByUserIdAndIsRead(userId, null, pageable)` sinh SQL `is_read IS NULL` làm rỗng danh sách thông báo khi người dùng mở màn hình thông báo tổng thể. Cần dùng `findAllByUserId` khi `isRead == null`.
- **N2 (GroupTxnListener.java):**
  - Vi phạm ràng buộc DB `notifications.user_id NOT NULL` khi `transactorId == null` (chi quỹ, hoàn tiền).
  - Không tuân thủ Rule 4 của phân hệ Group ([rule.md](file:///D:/projects/java/server/docs/design/group/rule.md)) và đặc tả thông báo ([api.md#333d-record-request](file:///D:/projects/java/server/docs/design/group/api.md#333d-record-request)).
  - Trải nghiệm người dùng kém khi nhận thông báo hiển thị chuỗi UUID khó hiểu.
  - Sai type và tiêu đề khiến client hiển thị sai icon và điều hướng sai màn hình.
- **N3 (NotificationController.java:22):** Không bọc `ApiResponse`, trả về JSON không đồng bộ với tất cả các API khác của hệ thống, vi phạm Rule 2.4.
- **Goal & Budget Event Architecture:**
  - `GoalServiceImpl`: Đáp ứng nhu cầu thông báo hoàn thành mục tiêu tiết kiệm mà không làm dính kết nối trực tiếp với module `notification`.
  - `BudgetSpendImpl`: Loại bỏ việc ghi DB thông báo đồng bộ trong luồng tạo chi tiêu cá nhân, giải phóng thời gian phản hồi cho client và phân tách rõ ràng trách nhiệm ranh giới giữa các module (Domain Event Decoupling).

---

## 3. Bảng công việc

| Công việc | Trạng thái |
|---|---|
| Sửa `NotificationRepository.java` (thêm `findAllByUserId`) | Hoàn thành |
| Cập nhật `NotificationListResponse.java` (`items, meta`) | Hoàn thành |
| Cập nhật `NotificationServiceImpl.java` (rẽ nhánh null `isRead`) | Hoàn thành |
| Cập nhật `NotificationController.java` (`ApiResponse`) | Hoàn thành |
| Cập nhật `GroupTxnListener.java` (logic thông báo nhóm) | Hoàn thành |
| Tạo `GoalCompletedEvent.java` & cập nhật `GoalServiceImpl.java` | Hoàn thành |
| Tạo `GoalCompletedListener.java` | Hoàn thành |
| Tạo `BudgetAlertEvent.java` & cập nhật `BudgetSpendImpl.java` | Hoàn thành |
| Tạo `BudgetAlertListener.java` | Hoàn thành |
| Cập nhật `NotificationIntegrationTest.java` | Hoàn thành |
| Viết Unit Tests (`GroupTxnListenerTest`, `GoalCompletedListenerTest`, `BudgetAlertListenerTest`) | Hoàn thành |
| Cập nhật `BudgetSpendImplTest.java` | Hoàn thành |
| Cập nhật tài liệu `review-07-notification.md` | Hoàn thành |

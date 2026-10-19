# Hoàn thành module Budget (Ngân sách chi tiêu)

## Bối cảnh & Kết quả

- Module `budget` trước đây bị loại trừ khỏi quá trình build dự án tại [pom.xml](file:///D:/projects/java/server/pom.xml) do chưa đồng bộ với CSDL `migration_v2` và kiến trúc hiện tại (`category_code`, `LocalDate` với hỗ trợ múi giờ động, Spring Application Event, enum uppercase).
- **Hiện tại**: Module `budget` đã được refactor hoàn chỉnh sang `LocalDate` (`startDate`, `endDate`), giải quyết triệt để vấn đề múi giờ cho mọi người dùng qua `UserTimezoneHelper` (đọc header `X-Timezone` từ frontend), bổ sung đầy đủ phân hệ **Quản trị viên (Admin)** độc lập (`BudgetAdminService`, `BudgetAdminController`), tích hợp hoàn chỉnh vào hệ thống và test-compile thành công 100% (`BUILD SUCCESS` qua `mvn test-compile -DskipTests`).

---

## Các quy định & Kiến trúc đã áp dụng

1. **Định danh danh mục trong Ngân sách:** Chuyển hoàn toàn từ `categoryId` (UUID) sang `categoryCode` (String, VARCHAR(50)), khớp với bảng `categories(code)` trong [V2__categories_icon.sql](file:///D:/projects/java/server/src/main/resources/db/migration_v2/V2__categories_icon.sql) và `transactions(category_code)` trong [V3__wallet_transaction_recurring_recurring_scheduler_icon.sql](file:///D:/projects/java/server/src/main/resources/db/migration_v2/V3__wallet_transaction_recurring_recurring_scheduler_icon.sql).
2. **Dữ liệu danh mục trả về trong `BudgetRes` & `BudgetAdminRes`:** Sử dụng `CateSumaryRes` (gồm `id`, `name`, `code`, `icon`, `color`) để đồng bộ với cấu trúc response danh mục trong toàn bộ ứng dụng.
3. **Kiểu dữ liệu thời gian cho kỳ ngân sách (`start_date`, `end_date`) và Xử lý Múi giờ chuẩn xác cho mọi người dùng:**
   - **Lý do & Bản chất nghiệp vụ:** Ngân sách là khái niệm theo lịch dân dụng của người dùng (ví dụ: "Tháng 10" luôn bắt đầu từ ngày 01 đến ngày 31 theo ngày địa phương của người dùng, dù họ ở Việt Nam `+07:00` hay ở Mỹ `UTC-4`/`UTC-7`). Nếu lưu `Instant` UTC thì ngày bắt đầu của người dùng ở Mỹ sẽ bị lệch sang ngày khác khi lưu về UTC. Do đó, ngân sách lưu theo `LocalDate` (`DATE` trong PostgreSQL).
   - **Tên cột CSDL:** `start_date DATE NOT NULL`, `end_date DATE NOT NULL` trong bảng `budgets` ([V12__create_budgets.sql](file:///D:/projects/java/server/src/main/resources/db/migration_v2/V12__create_budgets.sql)).
   - **Thuộc tính Entity & DTO:** `LocalDate startDate; LocalDate endDate;`.
   - **Giải pháp xử lý Múi giờ (`UserTimezoneHelper`):**
     - Đọc header `X-Timezone` (ví dụ `America/New_York`, `Asia/Ho_Chi_Minh`) gửi kèm từ request của Client (Frontend Web/Mobile lấy tự động qua `Intl.DateTimeFormat().resolvedOptions().timeZone`). Nếu không có header, fallback mặc định an toàn là `Asia/Ho_Chi_Minh`.
     - **Khi tính toán chi tiêu (`BudgetSpentCalculator`):** Bảng `transactions` lưu `occurred_at TIMESTAMPTZ` (UTC). Hệ thống quy đổi `startDate` thành `Instant` lúc 00:00:00 của ngày đó theo đúng `ZoneId` của user, và `endDate` thành `Instant` lúc 23:59:59.999999999 theo `ZoneId` của user. Truy vấn `sumExpense` dùng index UTC của `transactions.occurred_at` với khoảng thời gian đã quy đổi chuẩn xác.
     - **Khi có giao dịch phát sinh (`BudgetSpendService`):** Lấy `occurredAt` (`Instant` UTC) chuyển đổi sang `LocalDate` theo múi giờ của người dùng (`occurredAt.atZone(userZone).toLocalDate()`), sau đó tìm các ngân sách có `startDate <= txnDate AND endDate >= txnDate`.
4. **Phân hệ Quản trị viên (Admin) độc lập:**
   - **Kiến trúc:** Tách riêng `BudgetAdminService` / `BudgetAdminServiceImpl` và `BudgetAdminController` tại endpoint `/budgets/admin`.
   - **Phân quyền:** Bảo vệ bằng `@PreAuthorize("hasAuthority(...) or hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")` sử dụng `PermissionName.BUDGET_READ` và `PermissionName.BUDGET_DELETE`.
   - **Chức năng Admin:**
     - `GET /budgets/admin`: Tra cứu phân trang ngân sách toàn hệ thống theo `user_id`, `is_active`, `is_deleted`, `period`.
     - `GET /budgets/admin/{id}`: Xem chi tiết bất kỳ ngân sách nào (bỏ qua kiểm tra quyền sở hữu).
     - `DELETE /budgets/admin/{id}`: Cưỡng chế xóa mềm hoặc vô hiệu hóa ngân sách vi phạm.
5. **Trạng thái kích hoạt và xóa:** `Budget` kế thừa [AssignedIdEntity.java](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/common/abstracts/AssignedIdEntity.java) (có sẵn `createdAt`, `updatedAt`, `isDeleted`). Ngoài ra, giữ cột `isActive` (BOOLEAN, default TRUE) để phân biệt giữa việc "người dùng tạm tắt ngân sách" (`isActive = false`) và "người dùng xóa ngân sách" (`isDeleted = true`).
6. **Quy tắc tính toán số tiền đã chi (`spent`) theo danh mục cha - con:**
   - *Khi tạo ngân sách:* Khi `startDate` ở quá khứ hoặc hiện tại, hệ thống tính `spent` ban đầu từ các giao dịch chi tiêu trong quá khứ của danh mục đó và toàn bộ danh mục con cháu (`path` tiền tố).
   - *Khi có giao dịch chi tiêu mới:* Giao dịch phát sinh ở danh mục con sẽ tự động cộng dồn tiền vào `spent` của cả ngân sách danh mục con và ngân sách danh mục cha (và mọi tổ tiên).
7. **Liên kết Transaction $\rightarrow$ Budget:** Sử dụng cơ chế Spring Application Event (`@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)`) lắng nghe `TxnCreateEvent`, `TxnUpdateEvent`, `TxnDeleteEvent` để cập nhật `BudgetSpendService.addExpense` / `removeExpense`. Decouple hoàn toàn module Transaction và Budget.
8. **Quy ước Enum:** Enum viết hoa (`WEEK`, `MONTH`, `QUARTER`, `YEAR` cho `PeriodType`; `NORMAL`, `NEAR_LIMIT`, `OVER_LIMIT` cho `BudgetStatus`), có `@JsonCreator` case-insensitive.
9. **Quy chuẩn Comment & Javadoc:** Toàn bộ Class, Interface, Method và các vị trí logic phức tạp đều có Javadoc / inline comment tiếng Việt chi tiết.
10. **Kiểm thử (Tests):** Đã cập nhật toàn bộ test của module `budget`, viết mới `BudgetAdminServiceImplTest` và `TransactionAffectedBudgetsIntegrationTest` theo đúng cú pháp `categoryCode`, `LocalDate`, `UserTimezoneHelper` và `CateSumaryRes`. Biên dịch thành công 100% qua `mvn test-compile -DskipTests` (không chạy test runtime theo đúng yêu cầu).

---

## Chi tiết các tệp đã triển khai / cập nhật

- **CSDL Migration:**
  - [V12__create_budgets.sql](file:///D:/projects/java/server/src/main/resources/db/migration_v2/V12__create_budgets.sql): Tạo bảng `budgets` chuẩn `migration_v2` với các cột `start_date DATE`, `end_date DATE`, ràng buộc và index.
- **Entity & Enums:**
  - [Budget.java](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/budget/entity/Budget.java)
  - [PeriodType.java](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/budget/enums/PeriodType.java)
  - [BudgetStatus.java](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/budget/enums/BudgetStatus.java)
  - [PermissionName.java](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/user/enums/PermissionName.java): Bổ sung `Resource.BUDGET`, quyền `BUDGET_READ` và `BUDGET_DELETE`.
- **DTOs & Mappers:**
  - [CreateBudgetRequest.java](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/budget/dto/request/CreateBudgetRequest.java)
  - [UpdateBudgetRequest.java](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/budget/dto/request/UpdateBudgetRequest.java)
  - [BudgetRes.java](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/budget/dto/response/BudgetRes.java)
  - [BudgetAdminRes.java](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/budget/dto/response/BudgetAdminRes.java)
  - [BudgetSpent.java](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/budget/helper/BudgetSpent.java)
  - [BudgetProgress.java](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/budget/helper/BudgetProgress.java)
  - [BudgetMapper.java](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/budget/mapper/BudgetMapper.java)
- **Repository & Helpers:**
  - [BudgetRepository.java](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/budget/repository/BudgetRepository.java): Bổ sung query `searchByAdmin` và `findByIdAndIsDeletedFalse`.
  - [BudgetCategoryHelper.java](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/budget/helper/BudgetCategoryHelper.java)
  - [BudgetSpentCalculator.java](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/budget/helper/BudgetSpentCalculator.java)
  - [UserTimezoneHelper.java](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/budget/helper/UserTimezoneHelper.java)
- **Controllers:**
  - [BudgetController.java](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/budget/controller/BudgetController.java): RESTful API người dùng (`/budgets`).
  - [BudgetAdminController.java](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/budget/controller/BudgetAdminController.java): RESTful API quản trị viên (`/budgets/admin`).
- **Services:**
  - [BudgetService.java](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/budget/service/BudgetService.java) & [BudgetImpl.java](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/budget/service/impl/BudgetImpl.java)
  - [BudgetAdminService.java](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/budget/service/BudgetAdminService.java) & [BudgetAdminServiceImpl.java](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/budget/service/impl/BudgetAdminServiceImpl.java)
  - [BudgetSpendService.java](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/budget/service/BudgetSpendService.java) & [BudgetSpendImpl.java](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/budget/service/impl/BudgetSpendImpl.java)
  - [BudgetOverviewService.java](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/budget/service/BudgetOverviewService.java) & [BudgetOverviewImpl.java](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/budget/service/impl/BudgetOverviewImpl.java)
  - [BudgetRenewalService.java](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/budget/service/BudgetRenewalService.java) & [BudgetRenewalImpl.java](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/budget/service/impl/BudgetRenewalImpl.java)
- **Events & Event Listeners:**
  - [TxnCreateEvent.java](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/transaction/events/TxnCreateEvent.java)
  - [TxnUpdateEvent.java](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/transaction/events/TxnUpdateEvent.java)
  - [TxnDeleteEvent.java](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/transaction/events/TxnDeleteEvent.java)
  - [TxnBehavierImpl.java](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/transaction/service/impl/TxnBehavierImpl.java)
  - [BudgetTxnEventListener.java](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/budget/events/listener/BudgetTxnEventListener.java)
- **Unit & Integration Test Updates:**
  - [BudgetStatusTest.java](file:///D:/projects/java/server/src/test/java/com/datn/financeapp/module/budget/enums/BudgetStatusTest.java)
  - [PeriodTypeTest.java](file:///D:/projects/java/server/src/test/java/com/datn/financeapp/module/budget/enums/PeriodTypeTest.java)
  - [BudgetAlertTextTest.java](file:///D:/projects/java/server/src/test/java/com/datn/financeapp/module/budget/helper/BudgetAlertTextTest.java)
  - [BudgetProgressTest.java](file:///D:/projects/java/server/src/test/java/com/datn/financeapp/module/budget/helper/BudgetProgressTest.java)
  - [BudgetSpentCalculatorTest.java](file:///D:/projects/java/server/src/test/java/com/datn/financeapp/module/budget/helper/BudgetSpentCalculatorTest.java)
  - [BudgetRepositoryTest.java](file:///D:/projects/java/server/src/test/java/com/datn/financeapp/module/budget/repository/BudgetRepositoryTest.java)
  - [BudgetImplTest.java](file:///D:/projects/java/server/src/test/java/com/datn/financeapp/module/budget/service/impl/BudgetImplTest.java)
  - [BudgetAdminServiceImplTest.java](file:///D:/projects/java/server/src/test/java/com/datn/financeapp/module/budget/service/impl/BudgetAdminServiceImplTest.java)
  - [BudgetOverviewImplTest.java](file:///D:/projects/java/server/src/test/java/com/datn/financeapp/module/budget/service/impl/BudgetOverviewImplTest.java)
  - [BudgetRenewalImplTest.java](file:///D:/projects/java/server/src/test/java/com/datn/financeapp/module/budget/service/impl/BudgetRenewalImplTest.java)
  - [BudgetSpendImplTest.java](file:///D:/projects/java/server/src/test/java/com/datn/financeapp/module/budget/service/impl/BudgetSpendImplTest.java)
  - [BudgetCrudIntegrationTest.java](file:///D:/projects/java/server/src/test/java/com/datn/financeapp/module/budget/controller/BudgetCrudIntegrationTest.java)
  - [BudgetPrivacyIntegrationTest.java](file:///D:/projects/java/server/src/test/java/com/datn/financeapp/module/budget/controller/BudgetPrivacyIntegrationTest.java)
  - [TransactionAffectedBudgetsIntegrationTest.java](file:///D:/projects/java/server/src/test/java/com/datn/financeapp/module/transaction/controller/TransactionAffectedBudgetsIntegrationTest.java)
- **Build Configuration:**
  - [pom.xml](file:///D:/projects/java/server/pom.xml): Bỏ loại trừ `module/budget/**` trong mã nguồn chính.

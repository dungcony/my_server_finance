# Kế hoạch Rà soát, Sửa lỗi và Bổ sung Test Toàn diện (Unit & Integration)

## 1. Hiện trạng Thực tế Tuyệt vời

- **Toàn bộ 399 files mã nguồn chính (`src/main/java`)** đã được hoàn thiện bởi bạn và biên dịch thành công 100%.
- **Toàn bộ cấu hình loại trừ (`<excludes>` và `<testExcludes>`) trong `pom.xml` đã được gỡ bỏ hoàn toàn**.
- **Toàn bộ 117 files test trong dự án** thuộc tất cả các module (`auth`, `user`, `category`, `icon`, `wallet`, `transaction`, `group`, `notification`, `budget`, `debt`, `goal`, `report`, `recurring`, `scheduler`) đã được phân loại vào đúng 2 thư mục `unitTest/` và `integrationTest/` và **đều biên dịch thành công 100% (`BUILD SUCCESS`)**!
- **Script kiểm thử `test.ps1` đã được nâng cấp toàn diện** để tương thích 100% với cấu trúc thư mục mới `unitTest/` và `integrationTest/`.

---

## 2. Nguyên tắc Bất di bất dịch

1. **TUYỆT ĐỐI KHÔNG CHỈNH SỬA / REVERT BẤT KỲ FILE NÀO TRONG `src/main/java`**. Mọi công việc chỉ diễn ra trong `src/test/java`, `test.ps1` và cấu hình test.
2. **Tuân thủ quy tắc kiểm thử (`test-rule.md`)**:
   - Không tự ý chạy `mvn test` trên toàn project khi chưa có sự đồng ý của bạn.
   - Luôn biên dịch xác minh bằng `mvn test-compile`.
   - Khi cần chạy kiểm thử, cung cấp cú pháp lệnh cụ thể (PowerShell / `test.ps1`) để bạn chủ động kiểm tra.
3. **Phân tách trách nhiệm rõ ràng**:
   - `unitTest/`: Kiểm thử Mockito thuần túy, nhanh, độc lập, không phụ thuộc DB/Spring context.
   - `integrationTest/`: Kiểm thử tích hợp với `@SpringBootTest`, `@Testcontainers` (PostgreSQL 16), kiểm tra API thực tế và trigger DB.

---

## 3. Danh sách Rà soát & Bổ sung Test Chi tiết theo Module

### Nhóm 1: Bổ sung các bài Unit Test còn thiếu
1. **Module Wallet**:
   - Hiện trạng: Chỉ có Integration Test, hoàn toàn chưa có Unit Test.
   - Cần bổ sung:
     - `unitTest/wallet/service/impl/WalletServiceImplTest.java`: Mock kiểm tra tạo ví, cập nhật số dư, soft-delete, chặn thao tác chi tiêu từ ví loại `SAVINGS`.
     - `unitTest/wallet/controller/WalletControllerTest.java`: Kiểm tra validation `@Valid` đầu vào.
2. **Module Transaction**:
   - Hiện trạng: Đã có `TxnBehavierImplTest` và 8 Integration Tests.
   - Cần bổ sung:
     - `unitTest/transaction/service/impl/TxnQueryServiceImplTest.java`: Mock kiểm tra lọc giao dịch theo ngày (`from`, `to`), cây danh mục, phân trang, tính tổng thu/chi.
     - `unitTest/transaction/controller/TransactionControllerTest.java`.
3. **Module Auth & User**:
   - Cần bổ sung:
     - `unitTest/auth/service/impl/AuthServiceImplTest.java`: Mock kiểm tra cơ chế khóa tài khoản sau 5 lần đăng nhập sai, Token Rotation, chặn kích hoạt tài khoản `BLOCKED`, lowercase email.
4. **Module Notification**:
   - Cần bổ sung:
     - `unitTest/notification/service/impl/NotificationServiceImplTest.java`: Mock kiểm tra phân trang, đánh dấu đã đọc (`markRead`), bảo vệ cách ly dữ liệu cá nhân D-27.

### Nhóm 2: Rà soát & Tối ưu Test cho các Module nâng cao
1. **Module Goal**:
   - `SavingsGoalRepositoryTest.java`: Kiểm tra entity kế thừa `AssignedIdEntity`, trigger DB tự động đồng bộ `saved_amount` khi có giao dịch nạp tiền.
   - `GoalContributionIntegrationTest.java`: Kiểm tra giao dịch liên kết với mục tiêu tiết kiệm.
2. **Module Debt**:
   - `DebtLifecycleIntegrationTest.java`: Kiểm tra luồng vay/nợ, danh mục hệ thống `cho_vay` / `di_vay`, ngày `LocalDate`.
   - `DebtPaymentIntegrationTest.java`: Kiểm tra trigger DB tự động cộng dồn `paid_amount` khi có giao dịch trả nợ.
   - `DebtRepositoryTest.java`: Kiểm tra query lọc `is_deleted = false`.
3. **Module Budget**:
   - Rà soát các unit test (`BudgetOverviewImplTest`, `BudgetRenewalImplTest`, `BudgetSpendImplTest`) và integration test (`BudgetCrudIntegrationTest`, `BudgetAlertIntegrationTest`).
4. **Module Report**:
   - Rà soát `ExportJobRepositoryTest`, `ReportSummaryIntegrationTest`, `ExportJobIntegrationTest` và các unit test report.
5. **Module Scheduler & Recurring**:
   - `BudgetRenewalJobIntegrationTest`, `DebtReminderJobIntegrationTest`, `WalletReconciliationJobIntegrationTest`.
   - `RecTxnRunnerIntegrationTest`, `RecTxnCrudIntegrationTest`.

---

## 4. Công cụ Hỗ trợ Kiểm thử: `test.ps1`

Script [test.ps1](file:///D:/projects/java/server/test.ps1) đã được cập nhật hoàn chỉnh:
- **Chạy Unit Test siêu tốc**: `.\test.ps1 -Unit` (quét toàn bộ `unitTest/`, không cần Docker).
- **Chạy Integration Test**: `.\test.ps1 -Integration` (quét toàn bộ `integrationTest/`, tự động kết nối Testcontainers).
- **Chạy theo Module**: 
  - `.\test.ps1 -Module wallet` (chạy cả unit và integration của wallet).
  - `.\test.ps1 -Module wallet -Unit` (chỉ chạy unit test của wallet).
  - `.\test.ps1 -Module wallet -Integration` (chỉ chạy integration test của wallet).
- **Chạy theo tên Class**: `.\test.ps1 -Name WalletCrudIntegrationTest`.
- **Menu tương tác thông minh**: Gõ `.\test.ps1` để chọn menu tương tác (hiển thị tag `[Unit]` / `[Integration]` cho từng class test).

---

## 5. Lộ trình Triển khai Đề xuất

- **Bước 1 (Ưu tiên cao)**: Bổ sung ngay các Unit Test còn thiếu ở Nhóm 1 (`wallet`, `transaction`, `auth`, `notification`).
- **Bước 2**: Rà soát, chuẩn hóa dữ liệu mock và assertions trong các bài test của Nhóm 2 (`goal`, `debt`, `budget`, `report`, `scheduler`, `recurring`).
- **Bước 3**: Chạy thử nghiệm từng bài test (hoặc theo từng module theo yêu cầu của bạn) để đảm bảo tất cả test cases đều PASS xanh 100%.

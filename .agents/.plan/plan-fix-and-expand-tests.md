# Kế hoạch Rà soát, Sửa lỗi và Bổ sung Test Toàn diện (Unit & Integration)

## 1. Hiện trạng Thực tế

- **Toàn bộ 437 files mã nguồn chính (`src/main/java`)**: Đã hoàn thiện và biên dịch thành công 100%.
- **Toàn bộ 132 files test trong dự án (`src/test/java`)**: Đã biên dịch thành công 100% (`BUILD SUCCESS`).
- **Unit Test**: Toàn bộ **530 / 530 unit tests PASSED 100%** (0 failures, 0 errors) khi chạy `.\test.ps1 -Unit`.
- **Integration Test Module Recurring**:
  - `RecTxnCrudIntegrationTest`: **6/6 tests PASSED 100%** (RECUR-01, RECUR-02, CRUD, skip/unskip, isolation, soft delete).
  - `RecTxnRunnerIntegrationTest`: **3/3 tests PASSED 100%** (RECUR-03, quét lịch đến hạn `processDueSchedules`, duy trì buffer 12 buổi, cô lập lỗi đơn lẻ, bỏ qua khoản disabled).
- **Integration Test Module Debt**:
  - `DebtRepositoryTest`: **4/4 tests PASSED 100%** (D-27 isolation, filter/sort, native scalar queries, outstanding due date query).
- **Script kiểm thử `test.ps1`**: Hoạt động mượt mà, hỗ trợ tự động kết nối Testcontainers qua WSL Docker / Windows Docker.

---

## 2. Nguyên tắc Bất di bất dịch

1. **TUYỆT ĐỐI KHÔNG CHỈNH SỬA / REVERT BẤT KỲ FILE NÀO TRONG `src/main/java`**. Mọi công việc chỉ diễn ra trong `src/test/java`, `test.ps1` và cấu hình test.
2. **Tuân thủ quy tắc kiểm thử (`test-rule.md`)**:
   - Biên dịch xác minh bằng `mvn test-compile`.
   - Cung cấp cú pháp lệnh cụ thể (PowerShell / `test.ps1`) để người dùng chủ động kiểm tra từng test/module.
3. **Phân tách trách nhiệm rõ ràng**:
   - `unitTest/`: Kiểm thử Mockito thuần túy, nhanh, độc lập, không phụ thuộc DB/Spring context.
   - `integrationTest/`: Kiểm thử tích hợp với `@SpringBootTest`, `@Testcontainers` (PostgreSQL 16), kiểm tra API thực tế và trigger DB.

---

## 3. Tiến độ Chi tiết theo Module

### Nhóm 1: Unit Test các Module
- [x] **Module User & Auth**: Đã chuẩn hóa toàn bộ unit tests (`AdminRoleServiceTest`, `AdminUserServiceTest`, `UserControllerTest`, `UserResTest`, `PasswordGeneratorTest`, `RolePermissionCacheHelperTest`, `UserAuthCacheHelperTest`, `UserServiceImplGetNamesTest`, `UserServiceImplValidUserTest`).
- [x] **Module Wallet**: Đã chuẩn hóa `WalletControllerTest`, `WalletBehavierImplTest`, `WalletServiceImplTest`.
- [x] **Module Transaction & Recurring**: Đã chuẩn hóa `TxnBehavierImplTest`.
- [x] **Module Group**: Đã chuẩn hóa `MemberServiceImplTest`, `GroupServiceImplTest`, `GroupTxnCreateImplTest`, `MemberControllerTest`, `GroupTxnMapperControllerTest`, `GroupReportServiceTest`.
- [x] **Module Budget & Category**: Đã chuẩn hóa `CateImplTest`, `BudgetAdminServiceImplTest`, `BudgetImplTest`, `BudgetSpentCalculatorTest`.
- **Kết quả Unit Test**: **530 / 530 tests PASSED** trên toàn bộ test suite.

### Nhóm 2: Integration Test các Module nâng cao
- [x] **Module Recurring**:
  - [x] `RecTxnCrudIntegrationTest`: Khớp 100% kiến trúc `RecController`, kiểm thử tạo lịch không sinh giao dịch tức thì, bật/tắt `enable`/`disable`, bỏ qua/khôi phục lịch con `skip`/`unskip`, bảo vệ cách ly 404, kiểm tra danh mục leaf, xóa mềm và dọn dẹp lịch con.
  - [x] `RecTxnRunnerIntegrationTest`: Khớp 100% `RecTxnScheduleService.processDueSchedules()`, kiểm tra quét lịch đến hạn sinh giao dịch, duy trì gối đầu buffer 12 buổi, cô lập lỗi buổi hỏng sang FAILED mà không ảnh hưởng buổi lành, tự động chuyển lịch sang SKIPPED khi khoản định kỳ bị disable.
- [x] **Module Debt**:
  - [x] `DebtRepositoryTest`: Khớp `Debt`, `Wallet`, `Transaction`, `User` entity builders. Đã chạy PASS 100% với Testcontainers PostgreSQL 16.
  - [ ] `DebtLifecycleIntegrationTest`
  - [ ] `DebtPaymentIntegrationTest`
- [ ] **Module Goal**:
  - [ ] `SavingsGoalRepositoryTest`
  - [ ] `GoalContributionIntegrationTest`
- [ ] **Module Budget**:
  - [ ] `BudgetCrudIntegrationTest`
  - [ ] `BudgetAlertIntegrationTest`
- [ ] **Module Scheduler**:
  - [ ] `BudgetRenewalJobIntegrationTest`
  - [ ] `DebtReminderJobIntegrationTest`
  - [ ] `WalletReconciliationJobIntegrationTest`
- [ ] **Module Report**:
  - [ ] `ExportJobRepositoryTest`
  - [ ] `ReportSummaryIntegrationTest`
  - [ ] `ExportJobIntegrationTest`

---

## 4. Công cụ Hỗ trợ Kiểm thử: `test.ps1`

- **Chạy Unit Test siêu tốc**: `.\test.ps1 -Unit`
- **Chạy Integration Test**: `.\test.ps1 -Integration`
- **Chạy theo tên Class**: `.\test.ps1 -Name <TenTestClass>`
- **Chạy theo Module**: `.\test.ps1 -Module <TenModule>`

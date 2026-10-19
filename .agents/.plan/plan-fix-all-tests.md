# Kế hoạch sửa toàn bộ test cho các module trong dự án

## Bối cảnh

Hệ thống đã hoàn thành việc tái cấu trúc mã nguồn toàn diện:
1. **Kiến trúc dữ liệu (`migration_v2`)**: Chuyển đổi toàn bộ quan hệ khoá ngoại từ `category_id` (UUID) và `icon_id` (UUID) sang chuỗi chuẩn `category_code` và `icon_code`.
2. **Loại bỏ Cache in-memory**: Xoá bỏ hoàn toàn `CateCache` và `CategoryCodeView`.
3. **Phân tách tầng Service**: Tách rõ ràng giữa luồng ghi/thay đổi (`BehavierService`) và luồng truy vấn đọc (`Service`). Ví dụ: `CategoryBehavierService` / `CategoryService`, `IconBehavierService` / `IconService`, `TxnBehavierService`.
4. **Chuẩn hoá Entity & Trigger**:
   - `Transaction`: Bỏ `TransactionWriter`, chuyển sang `TxnBehavierService`, phát event `TxnCreateEvent`/`TxnUpdateEvent`/`TxnDeleteEvent`.
   - `Wallet`: Thêm loại ví `SAVINGS`, DTO dùng `balance` và `iconCode`.
   - `Debt` & `Goal`: Kế thừa `AssignedIdEntity`, tích hợp `V13` và `V14` với trigger tự động đồng bộ số dư.
   - `User` & `Auth`: Chuẩn hoá `toLowerCase()` (bỏ `.trim()`), chặn kích hoạt tài khoản `BLOCKED`, bổ sung tính năng Admin `unlockUser`.

**Hiện trạng bộ test (`src/test/java`)**:
- Cấu hình kiểm thử `src/test/resources/application-test.yml` vẫn trỏ Flyway về `classpath:db/migration` (v1 cũ).
- Trong `pom.xml`, hầu hết các thư mục test đang bị tạm thời loại trừ qua `<testExcludes>` để tránh làm gián đoạn biên dịch (`auth`, `category`, `group`, `wallet`, `icon`, `user`, `notification`, `recurring`, `debt`, `goal`, `scheduler`).
- Một số file test cũ tham chiếu tới các class đã xoá (`CateCacheTest.java`, `TransactionWriterTest.java`).
- Các integration test và unit test chưa được cập nhật theo `category_code`, `icon_code`, DTO mới và các Service mới.

---

## Quyết định đã chốt

1. **Cấu hình Flyway kiểm thử**:
   - Sửa `src/test/resources/application-test.yml`: `spring.flyway.locations: classpath:db/migration_v2`.
2. **Dọn dẹp file test mồ côi**:
   - Xoá `src/test/java/com/datn/financeapp/module/category/helper/CateCacheTest.java`.
   - Xoá `src/test/java/com/datn/financeapp/module/transaction/helper/TransactionWriterTest.java`.
3. **Chiến lược thực hiện theo từng Phase**:
   - Để kiểm soát chất lượng và tránh xáo trộn quá nhiều file cùng lúc, việc sửa test được chia thành **6 Phase độc lập**.
   - Mỗi Phase hoàn thành sẽ mở `<testExclude>` tương ứng trong `pom.xml`, biên dịch `mvn test-compile` sạch sẽ và xác nhận trước khi chuyển sang Phase tiếp theo.
4. **Quy tắc chạy test**:
   - Tuân thủ nghiêm ngặt `test-rule.md`: Tuyệt đối không tự ý chạy test khi chưa có yêu cầu của người dùng; khi cần kiểm tra, cung cấp lệnh PowerShell để người dùng quyết định hoặc chạy test cục bộ theo phạm vi vừa sửa.

---

## 1. Các file sẽ thay đổi theo từng Phase

### Phase 0: Hạ tầng kiểm thử & Dọn dẹp
- `src/test/resources/application-test.yml`: Sửa `flyway.locations` thành `classpath:db/migration_v2`.
- `src/test/java/com/datn/financeapp/module/category/helper/CateCacheTest.java`: **XOÁ FILE**.
- `src/test/java/com/datn/financeapp/module/transaction/helper/TransactionWriterTest.java`: **XOÁ FILE**.
- `pom.xml`: Quản lý danh sách `<testExcludes>` theo từng Phase được mở.

### Phase 1: Module Category & Icon
- `src/test/java/com/datn/financeapp/module/category/controller/CategoryControllerTest.java`: Mock `CategoryBehavierService` thay vì `CategoryService`.
- `src/test/java/com/datn/financeapp/module/category/service/impl/CateImplTest.java`: Cập nhật test theo `CategoryService.findByCodeAndIsDeletedAndIsLeaf`.
- `src/test/java/com/datn/financeapp/module/category/service/impl/CateBehavierImplTest.java` *(file mới)*: Test các nghiệp vụ `create`, `update`, `delete`, `getMasters`, `getOfParent` của `CateBehavierImpl`.
- `src/test/java/com/datn/financeapp/module/category/controller/CategoryTreeIntegrationTest.java`: Test tích hợp tạo cây danh mục theo `code`.
- `src/test/java/com/datn/financeapp/module/category/repository/CategoryRepositoryTest.java`: Test các query theo `code` và `path`.
- `src/test/java/com/datn/financeapp/module/icon/controller/IconControllerTest.java`: Sửa endpoint `/{code}` và kiểu dữ liệu `code` cho update/delete.
- `src/test/java/com/datn/financeapp/module/icon/service/impl/IconBehavierImplTest.java`: Sửa tham số truyền sang `code`, mock `findByCode`.
- `src/test/java/com/datn/financeapp/module/icon/service/impl/IconImplTest.java`: Viết lại test cho 3 hàm: `findByCode`, `findIconByCode`, `findIconsByCodes`.

### Phase 2: Module Wallet & Transaction
- `src/test/java/com/datn/financeapp/module/wallet/controller/WalletCrudIntegrationTest.java`: Đổi `initial_balance` $\rightarrow$ `balance`, bắt buộc `icon_code`, enum `BANK`/`CASH`, assert `balance`.
- `src/test/java/com/datn/financeapp/module/wallet/controller/WalletAccessControlIntegrationTest.java`: Chuẩn hoá helper `createWallet`.
- `src/test/java/com/datn/financeapp/module/wallet/controller/WalletFutureBalanceIntegrationTest.java`: Bỏ inject `TransactionWriter`.
- `src/test/java/com/datn/financeapp/module/wallet/repository/WalletRepositoryTest.java`: Cập nhật entity builder ví.
- `src/test/java/com/datn/financeapp/module/transaction/service/impl/TxnBehavierImplTest.java` *(file mới)*: Test CRUD transaction, kiểm tra leaf category, chặn chi tiêu từ ví `SAVINGS`.
- `src/test/java/com/datn/financeapp/module/transaction/controller/TransactionCrudIntegrationTest.java`: Đổi `category_id` $\rightarrow$ `category_code`, enum hoa (`EXPENSE`, `INCOME`).
- `src/test/java/com/datn/financeapp/module/transaction/controller/TransactionBulkIntegrationTest.java`: Đổi `category_id` $\rightarrow$ `category_code`.
- `src/test/java/com/datn/financeapp/module/transaction/controller/TransactionCategoryTreeFilterIntegrationTest.java`: Đổi `category_id` $\rightarrow$ `category_code`.
- `src/test/java/com/datn/financeapp/module/transaction/controller/TransactionDeleteDebtLinkIntegrationTest.java`: Đổi `category_id` $\rightarrow$ `category_code`.
- `src/test/java/com/datn/financeapp/module/transaction/controller/TransactionListQueryIntegrationTest.java`: Đổi `category_id` $\rightarrow$ `category_code`.
- `src/test/java/com/datn/financeapp/module/transaction/controller/TransactionUpdateAtomicityIntegrationTest.java`: Đổi `category_id` $\rightarrow$ `category_code`.
- `src/test/java/com/datn/financeapp/module/transaction/repository/TransactionRepositoryTest.java`: Đổi entity builder sang `categoryCode`.

### Phase 3: Module User & Auth
- `src/test/java/com/datn/financeapp/module/auth/controller/AuthEmailVerificationIntegrationTest.java`: Bổ sung test U2 (email chữ hoa) và U3 (tài khoản `BLOCKED` không thể kích hoạt qua OTP).
- `src/test/java/com/datn/financeapp/module/user/controller/AdminBlockUserIntegrationTest.java`: Bổ sung test Admin mở khoá (`PATCH /manager/users/unlock`), kiểm tra xoá blacklist Redis và phân quyền cấp bậc.

### Phase 4: Module Group
- `src/test/java/com/datn/financeapp/module/group/service/impl/GroupTxnMapperCreateImplTest.java`: Mock `SecurityContextUtil.currentUserId()`, đổi `categoryId` $\rightarrow$ `categoryCode`.
- `src/test/java/com/datn/financeapp/module/group/service/impl/GroupTxnMapperImplTest.java`: Chuẩn hoá mapper với `categoryCode`.
- `src/test/java/com/datn/financeapp/module/group/events/listener/GroupTxNotificationListenerTest.java`: Cập nhật event `GroupTxnConfirmedEvent` với `String categoryCode`.
- `src/test/java/com/datn/financeapp/module/group/repository/GroupTxnMapperRepositoryTest.java`: Gán `categoryCode`.
- `src/test/java/com/datn/financeapp/module/group/service/GroupMemberBalanceSyncIntegrationTest.java`: Đổi sang `categoryCode`.
- `src/test/java/com/datn/financeapp/module/group/service/GroupPersonalSyncIntegrationTest.java`: Đổi sang `categoryCode`.
- `src/test/java/com/datn/financeapp/module/group/service/GroupScalePerfIntegrationTest.java`: Đổi placeholder template sang `categoryCode`.
- `src/test/java/com/datn/financeapp/module/group/service/GroupServicePerfIntegrationTest.java`: Đổi sang `categoryCode`.
- `src/test/java/com/datn/financeapp/module/group/service/GroupTransactionListQueryCountIntegrationTest.java`: Đổi sang `categoryCode`.
- `src/test/java/com/datn/financeapp/module/group/service/TxnBehavierServiceTest.java`: Đổi sang `categoryCode`.

### Phase 5: Module Recurring, Scheduler & Notification
- `src/test/java/com/datn/financeapp/module/recurring/controller/RecTxnCrudIntegrationTest.java`: Đổi `category_id` $\rightarrow$ `category_code`, `icon_id` $\rightarrow$ `icon_code`.
- `src/test/java/com/datn/financeapp/module/recurring/service/RecTxnRunnerIntegrationTest.java`: Đổi sang `category_code`.
- `src/test/java/com/datn/financeapp/module/scheduler/DebtReminderJobIntegrationTest.java`: Kiểm tra đồng bộ `LocalDate`.
- `src/test/java/com/datn/financeapp/module/notification/controller/NotificationIntegrationTest.java`: Kiểm tra format response và quyền xem thông báo.

### Phase 6: Module Debt & Goal
- `src/test/java/com/datn/financeapp/module/debt/controller/DebtLifecycleIntegrationTest.java`: Cập nhật gọi `TxnBehavierService`, dùng `LocalDate` và category hệ thống `cho_vay`/`di_vay`.
- `src/test/java/com/datn/financeapp/module/debt/controller/DebtPaymentIntegrationTest.java`: Cập nhật trả nợ qua `TxnBehavierService`, kiểm tra trigger đồng bộ `paid_amount`.
- `src/test/java/com/datn/financeapp/module/debt/repository/DebtRepositoryTest.java`: Lọc `is_deleted = false`.
- `src/test/java/com/datn/financeapp/module/goal/controller/GoalContributionIntegrationTest.java`: Cập nhật nạp tiền goal qua giao dịch `transfer`, kiểm tra trigger `saved_amount`.
- `src/test/java/com/datn/financeapp/module/goal/repository/SavingsGoalRepositoryTest.java`: Cập nhật entity builder với `AssignedIdEntity`.

---

## 2. Lý do thay đổi

- **Cấu hình Flyway cũ:** `application-test.yml` trỏ vào `db/migration` khiến khi khởi chạy Testcontainers, CSDL bị thiếu các bảng mới (`V12__create_budgets.sql`, `V13__create_debt.sql`, `V14__create_savings_goals.sql`) và cột `category_code`/`icon_code` không tồn tại, gây lỗi `column not found` hoặc `relation does not exist`.
- **Lỗi biên dịch do class đã xoá:** `CateCache` và `TransactionWriter` đã bị xoá khỏi `src/main`, nhưng các file test tương ứng vẫn còn tồn tại, nếu mở exclude sẽ lập tức gây vỡ build.
- **Không tương thích DTO và Service mới:**
  - Category Controller chuyển sang tiêm `CategoryBehavierService`.
  - Icon CRUD dùng `code` thay vì `UUID id`.
  - Giao dịch và Ngân sách dùng `category_code` (String) thay cho `category_id` (UUID).
  - Ví yêu cầu `balance` và `icon_code`, bổ sung loại ví `SAVINGS`.
  - Nợ và Mục tiêu tiết kiệm chuyển sang dùng `TxnBehavierService` thay vì `TransactionWriter`.

---

## 3. Hướng giải quyết / code xem trước

### Bước 1: Chuẩn hoá `application-test.yml` (Phase 0)
```yaml
spring:
  flyway:
    locations: classpath:db/migration_v2
    encoding: UTF-8
    baseline-on-migrate: false
```

### Bước 2: Viết test cho `CateBehavierImplTest` (Phase 1)
```java
@ExtendWith(MockitoExtension.class)
class CateBehavierImplTest {
    @Mock private CategoryRepository categoryRepository;
    @Mock private IconService iconService;
    @Mock private CateMapper cateMapper;
    private CateBehavierImpl service;

    @BeforeEach
    void setUp() {
        service = new CateBehavierImpl(categoryRepository, iconService, cateMapper);
    }
    // Test create, update, delete, getMasters, getOfParent
}
```

### Bước 3: Sửa Wallet & Transaction Test Payload (Phase 2)
```java
// Tạo ví mới với DTO chuẩn
Map<String, Object> body = Map.of(
    "name", "Ví Tiền Mặt",
    "type", "CASH",
    "balance", 1_000_000,
    "icon_code", "default_wallet",
    "color", "#2f8272"
);

// Tạo giao dịch chi tiêu với category_code
Map<String, Object> txnBody = Map.of(
    "type", "EXPENSE",
    "amount", 50_000,
    "wallet_id", walletId,
    "category_code", "an_uong",
    "occurred_at", Instant.now().toString()
);
```

### Bước 4: Mock SecurityContext cho Group Create Test (Phase 4)
```java
SecurityContext context = Mockito.mock(SecurityContext.class);
Authentication auth = Mockito.mock(Authentication.class);
when(auth.getPrincipal()).thenReturn(new CustomUserDetails(memberId, ...));
when(context.getAuthentication()).thenReturn(auth);
SecurityContextHolder.setContext(context);
```

---

## 4. Danh sách công việc chi tiết theo từng Phase

- [ ] **Phase 0: Hạ tầng kiểm thử & Dọn dẹp**
  - Sửa `application-test.yml` trỏ vào `classpath:db/migration_v2`.
  - Xoá `CateCacheTest.java`.
  - Xoá `TransactionWriterTest.java`.
- [ ] **Phase 1: Module Category & Icon**
  - Mở exclude `category/**` và `icon/**` trong `pom.xml`.
  - Cập nhật `CategoryControllerTest.java`, `CateImplTest.java`, `CategoryTreeIntegrationTest.java`, `CategoryRepositoryTest.java`.
  - Viết `CateBehavierImplTest.java`.
  - Cập nhật `IconControllerTest.java`, `IconBehavierImplTest.java`, `IconImplTest.java`.
  - Xác minh biên dịch Phase 1 sạch sẽ (`mvn test-compile`).
- [ ] **Phase 2: Module Wallet & Transaction**
  - Mở exclude `wallet/**` và các test transaction trong `pom.xml`.
  - Cập nhật 4 file test module Wallet (`WalletCrudIntegrationTest`, `WalletAccessControlIntegrationTest`, `WalletFutureBalanceIntegrationTest`, `WalletRepositoryTest`).
  - Viết mới `TxnBehavierImplTest.java`.
  - Cập nhật 7 integration test module Transaction và `TransactionRepositoryTest.java`.
  - Xác minh biên dịch Phase 2 sạch sẽ.
- [ ] **Phase 3: Module User & Auth**
  - Mở exclude `auth/**` và `user/**` trong `pom.xml`.
  - Thêm test U2 (email hoa), U3 (chặn kích hoạt tài khoản `BLOCKED`) trong `AuthEmailVerificationIntegrationTest.java`.
  - Thêm test Admin mở khoá tài khoản trong `AdminBlockUserIntegrationTest.java`.
  - Xác minh biên dịch Phase 3 sạch sẽ.
- [ ] **Phase 4: Module Group**
  - Mở exclude `group/**` trong `pom.xml`.
  - Cập nhật `GroupTxnMapperCreateImplTest.java`, `GroupTxnMapperImplTest.java`, `GroupTxNotificationListenerTest.java`, `GroupTxnMapperRepositoryTest.java`.
  - Cập nhật 5 test tích hợp cân đối số dư và hiệu năng.
  - Xác minh biên dịch Phase 4 sạch sẽ.
- [ ] **Phase 5: Module Recurring, Scheduler & Notification**
  - Mở exclude `recurring/**`, `scheduler/**`, `notification/**` trong `pom.xml`.
  - Cập nhật `RecTxnCrudIntegrationTest.java`, `RecTxnRunnerIntegrationTest.java`, `DebtReminderJobIntegrationTest.java`, `NotificationIntegrationTest.java`.
  - Xác minh biên dịch Phase 5 sạch sẽ.
- [ ] **Phase 6: Module Debt & Goal**
  - Mở exclude `debt/**`, `goal/**` trong `pom.xml` (ở cả `<excludes>` mã nguồn chính và `<testExcludes>`).
  - Cập nhật test vòng đời và trả nợ trong module Debt.
  - Cập nhật test đóng góp mục tiêu và kiểm tra trigger trong module Goal.
  - Xác minh biên dịch Phase 6 sạch sẽ.

---

## Ngoài phạm vi

- Module `report`: Giữ nguyên `<exclude>` và `<testExclude>` do mã nguồn của `report` chưa được người dùng migrate sang `category_code`.
- Không thay đổi mã nguồn logic trong `src/main/java` trừ khi phát hiện bug thực tế trong quá trình kiểm thử.

---

## Kiểm tra

- **Quy tắc**: Không tự ý chạy test khi chưa có yêu cầu từ người dùng.
- Lệnh kiểm tra biên dịch cú pháp sau mỗi Phase:
  ```powershell
  mvn test-compile
  ```
- Lệnh chạy test cục bộ từng Phase (chỉ chạy khi người dùng yêu cầu):
  - Phase 1: `mvn test -Dtest=CategoryControllerTest,CateImplTest,CateBehavierImplTest,IconControllerTest,IconBehavierImplTest,IconImplTest`
  - Phase 2: `mvn test -Dtest=WalletCrudIntegrationTest,WalletAccessControlIntegrationTest,TxnBehavierImplTest,TransactionCrudIntegrationTest`
  - Phase 3: `mvn test -Dtest=AuthEmailVerificationIntegrationTest,AdminBlockUserIntegrationTest`
  - Phase 4: `mvn test -Dtest=GroupTxnMapperCreateImplTest,GroupTxNotificationListenerTest`
  - Phase 5: `mvn test -Dtest=RecTxnCrudIntegrationTest,NotificationIntegrationTest`
  - Phase 6: `mvn test -Dtest=DebtLifecycleIntegrationTest,GoalContributionIntegrationTest`

---

## Câu hỏi còn mở

- Không còn câu hỏi mở. Tất cả quy ước dữ liệu, trigger và DTO đều đã rõ ràng.

---

## Bảng công việc

| Phase | Công việc | Trạng thái |
|---|---|---|
| Phase 0 | Hạ tầng kiểm thử `application-test.yml` & dọn dẹp file test mồ côi | Chưa làm |
| Phase 1 | Sửa & mở test module Category & Icon | Chưa làm |
| Phase 2 | Sửa & mở test module Wallet & Transaction | Chưa làm |
| Phase 3 | Sửa & mở test module User & Auth | Chưa làm |
| Phase 4 | Sửa & mở test module Group | Chưa làm |
| Phase 5 | Sửa & mở test module Recurring, Scheduler & Notification | Chưa làm |
| Phase 6 | Sửa & mở test module Debt & Goal | Chưa làm |
| Tổng kết | Xác minh toàn bộ test pass biên dịch (`mvn test-compile`) | Chưa làm |

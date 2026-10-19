# Kế hoạch sửa toàn bộ test cho 7 module: Category, Icon, Wallet, Transaction, Group, Auth, User

## Bối cảnh

Sau các đợt refactor kiến trúc và tinh gọn dữ liệu gần đây:
1. **Category**: Đã xoá bỏ `CateCache` và `CategoryCodeView` (commit `0feee6c`) vì khoá ngoại của danh mục ở các bảng liên quan (`transactions`, `group_transactions`, `recurring_transactions`) chuyển sang dùng trực tiếp chuỗi `category_code` thay vì `category_id` (UUID). Các thao tác quản trị CRUD danh mục đã chuyển từ `CateImpl` sang `CategoryBehavierService` / `CateBehavierImpl`. `CategoryRepository.java` hiện còn sót `findAllCodes()` và import `CategoryCodeView` khiến Maven không thể compile.
2. **Icon**: Đã chuyển sang mô hình `IconBehavierService` (quản trị CRUD theo `code`, đường dẫn API `/icons/{code}`) và `IconService` (`findByCode`, `findIconByCode`, `findIconsByCodes`). Khoá ngoại icon ở `wallets` và `categories` đổi thành `icon_code`.
3. **Wallet**: DTO `WalletCreateReq` dùng `balance` (thay vì `initial_balance`), yêu cầu bắt buộc `iconCode`, enum viết hoa (`CASH`, `BANK`); `WalletRes` trả về `balance` (thay vì `current_balance`).
4. **Transaction**: Entity `Transaction` và các DTO (`TxnCreateReq`, `TxnUpdateReq`) đã chuyển `category_id` (UUID) thành `category_code` (String), bỏ các trường cũ không còn trong CSDL (`countsInReport`, `source`, `displayName` -> `name`). `TransactionWriter` đã bị thay thế hoàn toàn bởi `TxnBehavierService`.
5. **Group**: Entity `GroupTxn` và các sự kiện (`GroupTxnConfirmedEvent`, `GroupTxnBeingRejectEvent`) dùng `category_code`. `GroupTxnCreateImpl` lấy `operatorId` từ `SecurityContextUtil.currentUserId()` thay vì nhận tham số `userId`.
6. **Auth & User**: Đã chuẩn hoá email `toLowerCase()` (bỏ `.trim()`), chặn kích hoạt tài khoản `BLOCKED` qua OTP, bổ sung tính năng Admin mở khoá người dùng (`unlockUser`).
7. **Cấu hình kiểm thử (Test Config)**: `src/test/resources/application-test.yml` hiện vẫn cấu hình Flyway trỏ vào `classpath:db/migration` (v1 cũ với `category_id`, `icon_id`) thay vì `classpath:db/migration_v2` (khớp với entity hiện tại).

Hiện tại toàn bộ test thuộc 7 module trên đang bị lỗi biên dịch hoặc lỗi assertion/schema, cần được rà soát và khắc phục đồng bộ.

---

## Quyết định đã chốt

1. **Khắc phục lỗi compile ở src/main**:
   - Xoá method `findAllCodes()` và import `com.datn.financeapp.module.category.helper.CategoryCodeView` trong `CategoryRepository.java` (do `CateCache` đã bị xoá hoàn toàn).
2. **Chuyển Flyway test sang migration_v2**:
   - Sửa `src/test/resources/application-test.yml`: `spring.flyway.locations: classpath:db/migration_v2`.
3. **Xoá các file test của các class không còn tồn tại**:
   - Xoá `src/test/java/com/datn/financeapp/module/category/helper/CateCacheTest.java`.
   - Xoá `src/test/java/com/datn/financeapp/module/transaction/helper/TransactionWriterTest.java` (thay bằng `TxnBehavierImplTest.java`).
4. **Chuẩn hoá FK `category_code` & `icon_code` trong test**:
   - Toàn bộ payload request và SQL test data chuyển `category_id` (UUID) $\rightarrow$ `category_code` (String, VD `"an_uong"`).
   - Toàn bộ payload request và SQL test data tạo ví chuyển `icon_id` $\rightarrow$ `icon_code` (String, VD `"default_wallet"`, `"an_uong"`), `initial_balance` $\rightarrow$ `balance`.
5. **Cập nhật mock và test case theo đúng Service & Controller mới**:
   - `CategoryControllerTest`: mock `CategoryBehavierService`.
   - `CateImplTest`: test các phương thức của `CategoryService` (`findByCodeAndIsDeletedAndIsLeaf`).
   - Tạo mới `CateBehavierImplTest`: test logic `create`, `update`, `delete`, `getMasters`, `getOfParent` của `CateBehavierImpl`.
   - `IconControllerTest`: sửa endpoint patch/delete sang `/{code}` và mock `IconBehavierService.update(code, req)` / `delete(code)`.
   - `IconBehavierImplTest`: sửa tham số sang `code`, mock `findByCode` thay vì `findById`.
   - `IconImplTest`: test các phương thức mới của `IconService` (`findByCode`, `findIconByCode`, `findIconsByCodes`).
   - `GroupTxnMapperCreateImplTest`: mock SecurityContext cho `SecurityContextUtil.currentUserId()`, bỏ mock `categoryService.findIdByCode()`, assert `saved.getCategoryCode()`.

---

## 1. Các file sẽ thay đổi

### Code chính (để sửa lỗi biên dịch do xóa cache)
- `src/main/java/com/datn/financeapp/module/category/repository/CategoryRepository.java`: Xoá import `CategoryCodeView` và phương thức `findAllCodes()`.

### Cấu hình Test
- `src/test/resources/application-test.yml`: Sửa `flyway.locations` thành `classpath:db/migration_v2`.

### Module Category
- `src/test/java/com/datn/financeapp/module/category/helper/CateCacheTest.java`: **XOÁ FILE** (class `CateCache` không còn).
- `src/test/java/com/datn/financeapp/module/category/controller/CategoryControllerTest.java`: Chuyển sang mock `CategoryBehavierService`.
- `src/test/java/com/datn/financeapp/module/category/service/impl/CateImplTest.java`: Cập nhật test theo `CategoryService.findByCodeAndIsDeletedAndIsLeaf`, bỏ phụ thuộc `CateCache`.
- `src/test/java/com/datn/financeapp/module/category/service/impl/CateBehavierImplTest.java`: **FILE MỚI** (chuyển các test nghiệp vụ `create`, `update`, `delete`, `getMasters`, `getOfParent` từ `CateImplTest` sang).
- `src/test/java/com/datn/financeapp/module/category/controller/CategoryTreeIntegrationTest.java`: Bổ sung test case tích hợp cây danh mục theo `CategoryBehavierService`.
- `src/test/java/com/datn/financeapp/module/category/repository/CategoryRepositoryTest.java`: Viết test kiểm tra các query cây danh mục (`findByCodeIn`, `findDescendants`, `existsByNameIgnoreCaseAndIsDeletedFalseAndIdNot`).

### Module Icon
- `src/test/java/com/datn/financeapp/module/icon/controller/IconControllerTest.java`: Sửa route `/{code}` và kiểu dữ liệu `code` cho update/delete.
- `src/test/java/com/datn/financeapp/module/icon/service/impl/IconBehavierImplTest.java`: Sửa method call sang `code`, mock `findByCode`.
- `src/test/java/com/datn/financeapp/module/icon/service/impl/IconImplTest.java`: Viết lại test cho 3 phương thức `findByCode`, `findIconByCode`, `findIconsByCodes`.

### Module Wallet
- `src/test/java/com/datn/financeapp/module/wallet/controller/WalletCrudIntegrationTest.java`:
  - Request body `POST /wallets`: đổi `initial_balance` $\rightarrow$ `balance`, bổ sung `icon_code = "default_wallet"`, enum `type = "BANK"` / `"CASH"`.
  - Sửa assert `$.data.current_balance` $\rightarrow$ `$.data.balance`.
  - Request body `PATCH /wallets/{id}`: cập nhật theo `WalletupdateReq(name, iconCode, color)`.
- `src/test/java/com/datn/financeapp/module/wallet/controller/WalletAccessControlIntegrationTest.java`:
  - Cập nhật helper `createWallet` theo đúng DTO `WalletCreateReq`.
- `src/test/java/com/datn/financeapp/module/wallet/controller/WalletFutureBalanceIntegrationTest.java`:
  - Thay `TransactionWriter` bằng `TxnBehavierService` hoặc insert `Transaction` trực tiếp qua repository.
- `src/test/java/com/datn/financeapp/module/wallet/repository/WalletRepositoryTest.java`:
  - Rà soát và cập nhật test theo schema mới.

### Module Transaction
- `src/test/java/com/datn/financeapp/module/transaction/helper/TransactionWriterTest.java`: **XOÁ FILE** (thay bằng `TxnBehavierImplTest.java`).
- `src/test/java/com/datn/financeapp/module/transaction/service/impl/TxnBehavierImplTest.java`: **FILE MỚI** (test `TxnBehavierImpl` cho nghiệp vụ CRUD giao dịch).
- `src/test/java/com/datn/financeapp/module/transaction/controller/TransactionCrudIntegrationTest.java`:
  - Sửa `category_id` $\rightarrow$ `category_code`.
  - Sửa enum `type` thành chữ hoa (`EXPENSE`, `INCOME`).
  - Cập nhật hàm `systemCategoryId` $\rightarrow$ `systemCategoryCode` (lấy code từ bảng `categories`).
- `src/test/java/com/datn/financeapp/module/transaction/controller/TransactionBulkIntegrationTest.java`: Sửa `category_id` $\rightarrow$ `category_code`.
- `src/test/java/com/datn/financeapp/module/transaction/controller/TransactionCategoryTreeFilterIntegrationTest.java`: Sửa `category_id` $\rightarrow$ `category_code`.
- `src/test/java/com/datn/financeapp/module/transaction/controller/TransactionDeleteDebtLinkIntegrationTest.java`: Sửa `category_id` $\rightarrow$ `category_code`.
- `src/test/java/com/datn/financeapp/module/transaction/controller/TransactionListQueryIntegrationTest.java`: Sửa query param/field `category_id` $\rightarrow$ `category_code`.
- `src/test/java/com/datn/financeapp/module/transaction/controller/TransactionUpdateAtomicityIntegrationTest.java`: Sửa `category_id` $\rightarrow$ `category_code`.
- `src/test/java/com/datn/financeapp/module/transaction/controller/TransactionAffectedBudgetsIntegrationTest.java`: Cập nhật liên kết danh mục.
- `src/test/java/com/datn/financeapp/module/transaction/repository/TransactionRepositoryTest.java`:
  - Khởi tạo entity `Transaction` bằng `categoryCode(String)` thay vì `categoryId(UUID)`.
  - Cập nhật đúng các trường `occurredAt`, `name`, `type` (enum `TxnType`), bỏ `displayName`, `countsInReport`, `source`.

### Module Group
- `src/test/java/com/datn/financeapp/module/group/service/impl/GroupTxnMapperCreateImplTest.java`:
  - Xoá mock `categoryService.findIdByCode`.
  - Thiết lập mock SecurityContext cho `SecurityContextUtil.currentUserId()`.
  - Gọi `service.create(groupId, req)` với 2 tham số.
  - Assert `saved.getCategoryCode()`.
- `src/test/java/com/datn/financeapp/module/group/service/impl/GroupTxnMapperImplTest.java`: Sửa các test mapper liên quan đến `categoryCode`.
- `src/test/java/com/datn/financeapp/module/group/events/listener/GroupTxNotificationListenerTest.java`:
  - Cập nhật event constructor `GroupTxnConfirmedEvent` với `String categoryCode` thay vì `UUID categoryId`.
- `src/test/java/com/datn/financeapp/module/group/repository/GroupTxnMapperRepositoryTest.java`:
  - Sửa gán `.categoryCode(testCategory.getCode())` thay vì `.categoryId(...)`.
- `src/test/java/com/datn/financeapp/module/group/service/GroupMemberBalanceSyncIntegrationTest.java`:
  - Đổi biến và query kiểm tra sang `categoryCode`.
- `src/test/java/com/datn/financeapp/module/group/service/GroupPersonalSyncIntegrationTest.java`:
  - Đổi biến và query kiểm tra sang `categoryCode`.
- `src/test/java/com/datn/financeapp/module/group/service/GroupScalePerfIntegrationTest.java`:
  - Đổi placeholder `{category}` từ UUID sang category code string trong template SQL/JSON.
- `src/test/java/com/datn/financeapp/module/group/service/GroupServicePerfIntegrationTest.java`:
  - Đổi biến và query kiểm tra sang `categoryCode`.
- `src/test/java/com/datn/financeapp/module/group/service/GroupTransactionListQueryCountIntegrationTest.java`:
  - Đổi biến và query kiểm tra sang `categoryCode`.
- `src/test/java/com/datn/financeapp/module/group/service/TxnBehavierServiceTest.java`:
  - Sửa categoryId sang `String categoryCode = "an_uong"`.

### Module Auth & User
- `src/test/java/com/datn/financeapp/module/auth/controller/AuthEmailVerificationIntegrationTest.java`:
  - Bổ sung test đăng ký email chữ hoa (`Test.User@Example.COM`) thành công (U2).
  - Bổ sung test kích hoạt khi tài khoản bị khóa `BLOCKED` ném lỗi `AUTH_ACCOUNT_BLOCKED` (U3).
- `src/test/java/com/datn/financeapp/module/user/controller/AdminBlockUserIntegrationTest.java`:
  - Bổ sung test `PATCH /manager/users/unlock`: mở khoá tài khoản thành công, xóa khỏi Redis blacklist, evict auth cache.
  - Bổ sung test mở khoá tài khoản cấp cao hơn hoặc bằng mình bị từ chối `FORBIDDEN`.
  - Bổ sung test mở khoá tài khoản không bị khoá trả về lỗi.

---

## 2. Lý do thay đổi

| Module | Vị trí / Triệu chứng | Nguyên nhân gốc & Quy tắc |
|---|---|---|
| Category | `CategoryRepository.java:29` lỗi biên dịch | Commit `0feee6c` đã xoá `CateCache.java` và `CategoryCodeView.java` nhưng chưa dọn `findAllCodes()` ở repository. |
| Category | `CateImplTest.java` lỗi biên dịch | Mock `CateCache` và test các phương thức `create/update/delete` không còn nằm trong `CateImpl`. |
| Category | `CategoryControllerTest.java` lỗi biên dịch | `CategoryController` đã đổi sang tiêm `CategoryBehavierService` thay vì `CategoryService`. |
| Icon | `IconControllerTest.java` lỗi 405/404 | Controller mapping là `/{code}`, service nhận `String code`, nhưng test đang gọi `/{id}` và truyền `UUID`. |
| Icon | `IconBehavierImplTest.java` lỗi biên dịch | Service nhận `String code`, test đang truyền `UUID id`. |
| Icon | `IconImplTest.java` lỗi biên dịch | `IconImpl` đã thu gọn chỉ còn 3 phương thức tra cứu icon, test cũ gọi các phương thức đã bỏ (`listIcons`, `getActive`). |
| Wallet | `WalletCrudIntegrationTest.java` lỗi 400 | DTO `WalletCreateReq` bắt buộc `icon_code` và đổi `initial_balance` $\rightarrow$ `balance`. |
| Transaction | Toàn bộ test transaction lỗi compile/chạy | `Transaction.java` đổi `categoryId` (UUID) $\rightarrow$ `categoryCode` (String); `TransactionWriter` bị thay bằng `TxnBehavierService`. |
| Group | `GroupTxnMapperCreateImplTest.java` & events | `GroupTxn.java` và event dùng `categoryCode`. `create()` đổi sang lấy user từ security context. |
| Flyway Test | `application-test.yml:5` | Cấu hình trỏ nhầm `db/migration` (schema cũ v1) thay vì `db/migration_v2` (schema mới chuẩn hoá). |

---

## 3. Hướng giải quyết / code xem trước

### Bước 1: Dọn dẹp CategoryRepository.java
```java
// Xoá import CategoryCodeView
// Xoá phương thức:
// @Query("SELECT new com.datn.financeapp.module.category.helper.CategoryCodeView(c.id, c.code, c.isDeleted) FROM Category c")
// List<CategoryCodeView> findAllCodes();
```

### Bước 2: Sửa cấu hình Flyway trong application-test.yml
```yaml
spring:
  flyway:
    locations: classpath:db/migration_v2
    encoding: UTF-8
    baseline-on-migrate: false
```

### Bước 3: Sửa IconControllerTest & IconBehavierImplTest
```java
// IconControllerTest.java
@Test
void update_validRequest_returnsUpdated() throws Exception {
    String code = "tra_sua";
    IconUpdateReq req = new IconUpdateReq("Trà đào", "M2 2", "an_uong", "đào", 2, true);
    IconRes icon = new IconRes(UUID.randomUUID(), code, "Trà đào", "M2 2");
    when(iconBehavierService.update(eq(code), any(IconUpdateReq.class))).thenReturn(icon);

    mockMvc.perform(patch("/icons/" + code)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.display_name").value("Trà đào"));
}
```

### Bước 4: Sửa TransactionCrudIntegrationTest (category_code & balance)
```java
// TransactionCrudIntegrationTest.java
private String systemCategoryCode(String type) {
    return jdbcTemplate.queryForObject(
            "SELECT code FROM categories WHERE is_deleted = false AND is_leaf = true LIMIT 1", String.class);
}

@Test
void createExpenseTransaction_deductsWalletBalance_returns201() throws Exception {
    String token = registerAndGetAccessToken("tao.chi@example.com");
    String walletId = createWallet(token, "Ví Chi Tiêu", 500_000);
    String categoryCode = systemCategoryCode("EXPENSE");

    Map<String, Object> body = new HashMap<>();
    body.put("type", "EXPENSE");
    body.put("amount", 68000);
    body.put("wallet_id", walletId);
    body.put("category_code", categoryCode);
    body.put("occurred_at", Instant.now().toString());
    ...
}
```

### Bước 5: Mock SecurityContext cho GroupTxnMapperCreateImplTest
```java
// Mock SecurityContextUtil.currentUserId() bằng Mockito hoặc SecurityContextHolder
SecurityContext securityContext = Mockito.mock(SecurityContext.class);
Authentication authentication = Mockito.mock(Authentication.class);
when(authentication.getPrincipal()).thenReturn(new CustomUserDetails(memberId, ...));
when(securityContext.getAuthentication()).thenReturn(authentication);
SecurityContextHolder.setContext(securityContext);
```

---

## 4. Danh sách công việc chi tiết

1. **Chuẩn bị & Cấu hình nền tảng**:
   - [ ] Sửa `CategoryRepository.java` (bỏ `findAllCodes()` và import `CategoryCodeView`).
   - [ ] Sửa `application-test.yml` (chuyển Flyway sang `classpath:db/migration_v2`).
   - [ ] Xoá file thừa không còn class: `CateCacheTest.java`, `TransactionWriterTest.java`.
2. **Sửa cụm Category & Icon Tests**:
   - [ ] Sửa `CategoryControllerTest.java` (mock `CategoryBehavierService`).
   - [ ] Cập nhật `CateImplTest.java` (test `findByCodeAndIsDeletedAndIsLeaf`).
   - [ ] Viết mới `CateBehavierImplTest.java` cho `CategoryBehavierService`.
   - [ ] Sửa `IconControllerTest.java` (route `/{code}`, mock theo `code`).
   - [ ] Sửa `IconBehavierImplTest.java` (tham số `code`, mock `findByCode`).
   - [ ] Viết lại `IconImplTest.java` (test `findByCode`, `findIconByCode`, `findIconsByCodes`).
3. **Sửa cụm Wallet Tests**:
   - [ ] Sửa `WalletCrudIntegrationTest.java` (`balance`, `icon_code`, enum uppercase).
   - [ ] Sửa `WalletAccessControlIntegrationTest.java` (`createWallet` helper).
   - [ ] Sửa `WalletFutureBalanceIntegrationTest.java` (bỏ `TransactionWriter`).
   - [ ] Kiểm tra và cập nhật `WalletRepositoryTest.java`.
4. **Sửa cụm Transaction Tests**:
   - [ ] Viết mới `TxnBehavierImplTest.java` (thay cho `TransactionWriterTest.java`).
   - [ ] Sửa `TransactionCrudIntegrationTest.java` (`category_code`, `type` enum).
   - [ ] Sửa `TransactionBulkIntegrationTest.java` (`category_code`).
   - [ ] Sửa `TransactionCategoryTreeFilterIntegrationTest.java` (`category_code`).
   - [ ] Sửa `TransactionDeleteDebtLinkIntegrationTest.java` (`category_code`).
   - [ ] Sửa `TransactionListQueryIntegrationTest.java` (`category_code`).
   - [ ] Sửa `TransactionUpdateAtomicityIntegrationTest.java` (`category_code`).
   - [ ] Sửa `TransactionAffectedBudgetsIntegrationTest.java` (`category_code`).
   - [ ] Sửa `TransactionRepositoryTest.java` (entity builder theo fields mới).
5. **Sửa cụm Group Tests**:
   - [ ] Sửa `GroupTxnMapperCreateImplTest.java` (SecurityContext, `categoryCode`, bỏ `findIdByCode`).
   - [ ] Sửa `GroupTxNotificationListenerTest.java` (`categoryCode` trong event).
   - [ ] Sửa `GroupTxnMapperRepositoryTest.java` (`categoryCode`).
   - [ ] Sửa 5 integration tests: `GroupMemberBalanceSyncIntegrationTest`, `GroupPersonalSyncIntegrationTest`, `GroupScalePerfIntegrationTest`, `GroupServicePerfIntegrationTest`, `GroupTransactionListQueryCountIntegrationTest`.
   - [ ] Sửa `TxnBehavierServiceTest.java` (`categoryCode`).
6. **Sửa cụm Auth & User Tests**:
   - [ ] Bổ sung test U2 (email chữ hoa) và U3 (tài khoản BLOCKED không được kích hoạt qua OTP) vào `AuthEmailVerificationIntegrationTest.java`.
   - [ ] Bổ sung test Admin Unlock User vào `AdminBlockUserIntegrationTest.java`.
7. **Kiểm tra biên dịch & Test mục tiêu**:
   - [ ] Kiểm tra biên dịch toàn bộ test: `mvn test-compile`.

---

## Ngoài phạm vi

- Các module `debt`, `goal`, `budget`, `report`, `scheduler` (chưa thuộc 7 module người dùng yêu cầu).
- Không sửa đổi database schema trong production/dev.

---

## Kiểm tra

- **Quy tắc**: Luôn hỏi người dùng trước khi chạy test, không tự ý chạy test khi chưa có yêu cầu.
- Lệnh biên dịch kiểm tra cú pháp sau khi sửa:
  ```powershell
  mvn test-compile
  ```
- Lệnh chạy kiểm tra test từng module (khi người dùng yêu cầu):
  - Category & Icon: `mvn test -Dtest=CategoryControllerTest,CateImplTest,CateBehavierImplTest,IconControllerTest,IconBehavierImplTest,IconImplTest`
  - Wallet: `mvn test -Dtest=WalletCrudIntegrationTest,WalletAccessControlIntegrationTest,WalletFutureBalanceIntegrationTest`
  - Transaction: `mvn test -Dtest=TxnBehavierImplTest,TransactionCrudIntegrationTest`
  - Group: `mvn test -Dtest=GroupTxnMapperCreateImplTest,GroupTxNotificationListenerTest`
  - Auth & User: `mvn test -Dtest=AuthEmailVerificationIntegrationTest,AdminBlockUserIntegrationTest`

---

## Câu hỏi còn mở

- Hiện tại không còn câu hỏi mở. Tất cả quy ước (chuỗi `category_code`, `icon_code`, bỏ cache `CateCache`, phân tách service `Behavier` vs `Query`, Flyway `migration_v2`) đã được xác minh rõ ràng từ mã nguồn và chỉ đạo của người dùng.

---

## Bảng công việc

| Công việc | Trạng thái |
|---|---|
| Dọn dẹp CategoryRepository.java & cấu hình application-test.yml | Chưa làm |
| Xóa file test cũ CateCacheTest & TransactionWriterTest | Chưa làm |
| Sửa test module Category & Icon | Chưa làm |
| Sửa test module Wallet | Chưa làm |
| Sửa test module Transaction | Chưa làm |
| Sửa test module Group | Chưa làm |
| Sửa test module Auth & User | Chưa làm |
| Chạy `mvn test-compile` xác minh toàn bộ test pass biên dịch | Chưa làm |

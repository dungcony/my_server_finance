# Phase 2: Ví & Danh mục - Pattern Map

**Mapped:** 2026-08-23
**Files analyzed:** ~30 file mới/sửa (ước tính từ CONTEXT.md + ROADMAP + api/02-VI.md + api/03-DANH-MUC.md)
**Analogs found:** Toàn bộ có analog trong Phase 1 — không có nhóm nào hoàn toàn mới về kỹ thuật, trừ cách gọi `fn_category_tree` (native SQL function) — ghi rõ ở mục riêng bên dưới.

---

## 0. Việc bắt buộc đầu tiên — thay thế `WalletMinimal` (D-26)

Đây là plan wave đầu tiên theo D-29. Không viết code mới ở đây, chỉ **đổi entity/repository** dùng bởi 1 file main + 5 file test.

### Điểm chạm main

| File | Việc phải làm |
|---|---|
| `src/main/java/com/datn/financeapp/auth/service/AuthService.java` | Xoá import `common.wallet.WalletMinimal`/`WalletMinimalRepository`; đổi field `walletMinimalRepository` → repository mới của `wallet/`; sửa `register()` (dòng 94-107, đang dùng `WalletMinimal.builder()...isDeleted(false)...`) để build entity `Wallet` mới (thêm field `sortOrder` không có ở bản cũ); sửa `getMe()` (dòng 239, gọi `walletMinimalRepository.countByUserIdAndIsDeletedFalse(userId)`) → gọi repository mới, tên method có thể giữ nguyên hoặc đổi cho khớp convention `wallet/`. |

### Điểm chạm test (chỉ đổi `import` + tên field/type, không đổi logic test)

Cả 5 file đều theo đúng 1 khuôn: field `private WalletMinimalRepository walletMinimalRepository;`, dùng trong `@BeforeEach cleanTables() { walletMinimalRepository.deleteAll(); ... }`.

| File | Dòng cần sửa |
|---|---|
| `src/test/java/com/datn/financeapp/auth/AuthRegisterLoginIntegrationTest.java` | import dòng 11, field dòng 92, `deleteAll()` dòng 100, và đoạn assertion dùng `walletMinimalRepository.findAll()...getUserId()/getName()/getCurrentBalance()` (dòng 127-133) — các getter này tồn tại y hệt trên entity `Wallet` mới nên không cần đổi logic assertion, chỉ đổi type khai báo. |
| `src/test/java/com/datn/financeapp/auth/AuthLoginLockoutIntegrationTest.java` | import dòng 12, field dòng 91, `deleteAll()` dòng 103 |
| `src/test/java/com/datn/financeapp/auth/AuthProfilePasswordIntegrationTest.java` | import dòng 20, field dòng 63, `deleteAll()` dòng 78 |
| `src/test/java/com/datn/financeapp/auth/AuthRefreshRotationIntegrationTest.java` | import dòng 12, field dòng 96, `deleteAll()` dòng 104 |
| `src/test/java/com/datn/financeapp/auth/AuthIdempotencyRateLimitEndToEndTest.java` | import dòng 8, field dòng 66, `deleteAll()` dòng 78 |

**Sau khi thay xong, xoá hẳn** `src/main/java/com/datn/financeapp/common/wallet/WalletMinimal.java` và `WalletMinimalRepository.java` (package `common/wallet/` biến mất hoàn toàn) — chạy lại đủ 35 test Phase 1 xanh mới coi là xong bước này.

**Cột `Wallet` mới cần có** (từ `db/migration/V1__nen_tang.sql` dòng 87-113, đối chiếu `WalletMinimal.java` hiện tại):
`id, userId(user_id), groupId(group_id), name, type, initialBalance(initial_balance), currentBalance(current_balance), includeInTotal(include_in_total), icon, color(columnDefinition="bpchar(7)"), sortOrder(sort_order), isDeleted(is_deleted), createdAt(created_at)` — **giữ nguyên chú thích `bpchar(7)` cho `color`**, đây là cạm bẫy Hibernate `ddl-auto=validate` đã ghi rõ trong `WalletMinimal.java` dòng 58-61 (Postgres `CHAR(7)` báo physical type `bpchar`, không phải `char`/`varchar`).

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `wallet/entity/Wallet.java` | entity | CRUD | `auth/entity/User.java` (cấu trúc), `common/wallet/WalletMinimal.java` (đúng bảng, sẽ xoá) | exact (cột) |
| `wallet/repository/WalletRepository.java` | repository | CRUD + quyền SQL | `auth/repository/UserRepository.java` (CRUD cơ bản) + `auth/repository/RefreshTokenRepository.java` (native/JPQL có điều kiện, lock) | role-match |
| `wallet/service/WalletService.java` | service | CRUD + atomic balance update | `auth/service/AuthService.java` | exact (transaction pattern) |
| `wallet/controller/WalletController.java` | controller | request-response | `auth/controller/AuthController.java` | exact |
| `wallet/dto/*.java` (CreateWalletRequest, UpdateWalletRequest, WalletResponse, WalletSummaryResponse, TransferRequest, TransferResponse, AdjustBalanceRequest, AdjustBalanceResponse, ReconcileResponse, ReorderRequest) | DTO | request-response | `auth/dto/RegisterRequest.java`, `auth/dto/UserDetailDto.java` | exact |
| `wallet/mapper/WalletMapper.java` (nếu dùng MapStruct — discretion) | mapper | transform | Không có analog Phase 1 (Phase 1 map DTO thủ công trong Service) — xem mục "Không có analog" | none |
| `category/entity/Category.java` | entity | CRUD (cây 2 cấp) | `auth/entity/User.java` (cấu trúc) — quan hệ cha/con thì mới hoàn toàn | partial |
| `category/entity/CategoryGroup.java` | entity | CRUD (chỉ đọc, dữ liệu cố định) | `auth/entity/User.java` (cấu trúc) | role-match |
| `category/entity/Icon.java` | entity | CRUD (chỉ đọc) | `auth/entity/User.java` (cấu trúc) | role-match |
| `category/repository/CategoryRepository.java` | repository | CRUD + native SQL function | `auth/repository/RefreshTokenRepository.java` (mẫu native/JPQL query có tham số) | role-match |
| `category/repository/CategoryGroupRepository.java` | repository | CRUD (read-only) | `auth/repository/UserRepository.java` | role-match |
| `category/repository/IconRepository.java` | repository | CRUD (read-only, filter) | `auth/repository/UserRepository.java` | role-match |
| `category/service/CategoryService.java` | service | CRUD + cây validate | `auth/service/AuthService.java` | role-match |
| `category/controller/CategoryController.java` | controller | request-response | `auth/controller/AuthController.java` | exact |
| `category/dto/*.java` | DTO | request-response | `auth/dto/*.java` | exact |
| `common/exception/BusinessException` các mã lỗi mới (`WALLET_NAME_EXISTS`, `MAX_DEPTH_EXCEEDED`, ...) | exception (dùng lại class có sẵn) | — | `common/exception/BusinessException.java` (không sửa, chỉ dùng lại) | exact |
| Test tích hợp `wallet/*Test.java`, `category/*Test.java` | test | integration (Testcontainers) | `auth/AuthRegisterLoginIntegrationTest.java` | exact |
| Test đồng thời chuyển tiền (D-28 mục 1) | test | concurrency | Không có analog Phase 1 (Phase 1 không có test đa luồng) — xem mục "Không có analog" | none |

---

## Pattern Assignments

### `wallet/entity/Wallet.java` (entity, CRUD)

**Analog:** `src/main/java/com/datn/financeapp/auth/entity/User.java` (cấu trúc chuẩn) + `src/main/java/com/datn/financeapp/common/wallet/WalletMinimal.java` (đúng bảng `wallets`, sắp bị xoá — copy toàn bộ nội dung rồi bổ sung).

**Cấu trúc entity chuẩn** (từ `User.java` dòng 21-28):
```java
@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    private UUID id;
    ...
}
```

**Copy nguyên trạng từ `WalletMinimal.java`** (toàn bộ file, dòng 1-72), đổi tên class thành `Wallet`, thêm field `sortOrder` (đã có sẵn thật ra — `WalletMinimal` đã có `sortOrder`, D-26 chỉ nói "bổ sung sortOrder" nhưng thực tế cột đã tồn tại trong `WalletMinimal`; đối chiếu kỹ khi implement — có thể ý D-26 là đảm bảo entity mới **giữ lại** field này, không phải thêm mới). Giữ nguyên chú thích `bpchar(7)` cho `color` (dòng 58-61) — cạm bẫy Hibernate `ddl-auto=validate` đã xác nhận ở Phase 1.

**Id tự sinh ở tầng Service** (không dựa `DEFAULT gen_random_uuid()`), theo comment `User.java` dòng 16-19 — áp dụng y hệt cho `Wallet`.

---

### `wallet/repository/WalletRepository.java` (repository, CRUD + quyền SQL)

**Analog atomic update + lock:** `src/main/java/com/datn/financeapp/auth/repository/RefreshTokenRepository.java`

**Mẫu `PESSIMISTIC_WRITE` lock** (dòng 22-26):
```java
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.tokenHash = :hash AND rt.revokedAt IS NULL")
    Optional<RefreshToken> findActiveByTokenHashForUpdate(@Param("hash") String hash);
```
Áp dụng y hệt cho ví khi chuyển tiền — ví dụ:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT w FROM Wallet w WHERE w.id = :id AND NOT w.isDeleted")
Optional<Wallet> findByIdForUpdate(@Param("id") UUID id);
```
**Chú ý thứ tự lock khi chuyển tiền giữa 2 ví** — khoá theo thứ tự nhất quán (vd. sort theo `id`) để tránh deadlock khi hai giao dịch chuyển tiền ngược chiều nhau chạy đồng thời (A→B và B→A cùng lúc).

**Mẫu atomic UPDATE số dư** (`UPDATE ... SET current_balance = current_balance + :delta`) — Phase 1 **chưa có ví dụ atomic update trực tiếp trên số dư** (AuthService chỉ tạo ví số dư 0, không update). Đây là pattern **mới nhưng đã được ROADMAP/CONTEXT chỉ định rõ công thức** — viết bằng `@Modifying @Query`:
```java
@Modifying
@Query("UPDATE Wallet w SET w.currentBalance = w.currentBalance + :delta WHERE w.id = :id")
int adjustBalance(@Param("id") UUID id, @Param("delta") long delta);
```
Copy khuôn `@Modifying` từ `RefreshTokenRepository.revokeAllActiveForUser` (dòng 31-34):
```java
@Modifying
@Query("UPDATE RefreshToken rt SET rt.revokedAt = CURRENT_TIMESTAMP "
        + "WHERE rt.userId = :userId AND rt.revokedAt IS NULL")
int revokeAllActiveForUser(@Param("userId") UUID userId);
```

**Mẫu quyền trong SQL (D-27, CORE-05)** — chưa có analog Phase 1 (User không có khái niệm group). Viết mới theo đúng công thức D-27 đã cho sẵn, đặt trong mọi query select ví:
```java
@Query("SELECT w FROM Wallet w WHERE w.id = :id AND NOT w.isDeleted "
        + "AND (w.userId = :currentUser OR w.groupId IN "
        + "(SELECT gm.groupId FROM GroupMember gm WHERE gm.userId = :currentUser AND gm.status = 'active'))")
Optional<Wallet> findByIdForUser(@Param("id") UUID id, @Param("currentUser") UUID currentUser);
```
Lưu ý: `group_members` chưa có entity JPA ở Phase 2 (Group thuộc Phase 5) — nếu chưa tạo entity `GroupMember`, dùng native SQL subquery thẳng vào bảng `group_members` thay vì JPQL entity reference:
```java
@Query(value = "SELECT * FROM wallets w WHERE w.id = :id AND NOT w.is_deleted "
        + "AND (w.user_id = :currentUser OR w.group_id IN "
        + "(SELECT group_id FROM group_members WHERE user_id = :currentUser AND status = 'active'))",
        nativeQuery = true)
Optional<Wallet> findByIdForUser(@Param("id") UUID id, @Param("currentUser") UUID currentUser);
```
Vế nhóm luôn trả rỗng ở Phase 2 (đúng theo D-27) vì chưa ai từng `INSERT` vào `group_members` — không cần entity Group tồn tại để native query này chạy được, chỉ cần bảng đã có sẵn từ `V1__nen_tang.sql`.

---

### `wallet/service/WalletService.java` (service, CRUD + atomic balance update)

**Analog:** `src/main/java/com/datn/financeapp/auth/service/AuthService.java`

**`@Transactional` trên từng public method** (không ở mức class) — mẫu từ `AuthService.register()` dòng 75-76:
```java
@Transactional
public AuthResponse register(RegisterRequest req) {
```

**Mẫu kiểm tra trùng + ném BusinessException** (dòng 78-81):
```java
if (userRepository.existsByEmail(email)) {
    throw new BusinessException(
            "EMAIL_ALREADY_EXISTS", HttpStatus.CONFLICT.value(), "Email đã có người dùng.");
}
```
Áp dụng cho `WALLET_NAME_EXISTS` (409), `CATEGORY_NAME_EXISTS` (409), v.v. — tra bảng mã lỗi đầy đủ ở cuối `api/02-VI.md` và `api/03-DANH-MUC.md`.

**Mẫu build entity với `id` tự sinh + `Instant.now()` dùng chung** (dòng 83-107) — copy khuôn `Instant now = Instant.now();` rồi dùng lại cho cả entity chính lẫn entity phụ tạo trong cùng transaction (ví dụ: giao dịch transfer tạo cùng lúc với 2 lần update số dư).

**Mẫu chuyển tiền — 1 giao dịch DB cho nhiều thay đổi** (theo D-27 công thức + `api/02-VI.md` mục 8): viết một method `@Transactional` gọi lock cả 2 ví theo thứ tự cố định, tạo 1 record `type=transfer`, rồi 2 lần gọi `walletRepository.adjustBalance(...)` (atomic UPDATE, không load-modify-save) — **không** dùng `wallet.setCurrentBalance(x); walletRepository.save(wallet);` vì đó chính là load-modify-save bị cấm ở `CLAUDE.md` backend mục 3.

**Mẫu điều chỉnh số dư (WALLET-08, D-25 specifics)** — sinh giao dịch bù `source=adjustment`, `counts_in_report` theo request, gán 1 trong 2 danh mục hệ thống "Cập nhật số dư" (đã có sẵn trong DB từ `V8__dieu_chinh_so_du.sql`, tra bằng `category_group.name='Khác'` + `icon.code='vi_tien'` + đúng `type`). Không tạo gì khi `difference == 0` — trả `adjusted:false, transaction_id:null` theo đúng response mẫu ở `api/02-VI.md` mục 9.

**Mẫu `noRollbackFor` khi cần giữ side-effect qua nhánh lỗi** — copy nếu có nhánh tương tự "ghi log rồi mới throw" (tham khảo `AuthService.login()` dòng 123, `AuthService.refresh()` dòng 170) — Phase 2 khả năng dùng cho `reconcile` khi cần ghi log lệch số dư dù không auto_fix.

**Xoá mềm + validate "không xoá bản ghi cuối cùng"** — chưa có analog Phase 1 trực tiếp, nhưng theo đúng mẫu `BusinessException` ở trên, viết:
```java
long walletCount = walletRepository.countByUserIdAndIsDeletedFalse(userId);
if (walletCount <= 1) {
    throw new BusinessException("CANNOT_DELETE_LAST_WALLET", HttpStatus.CONFLICT.value(), "Phải còn ít nhất một ví.");
}
```
(method `countByUserIdAndIsDeletedFalse` đã tồn tại y hệt trên `WalletMinimalRepository` — copy sang `WalletRepository` mới.)

---

### `wallet/controller/WalletController.java` (controller, request-response)

**Analog:** `src/main/java/com/datn/financeapp/auth/controller/AuthController.java`

**Cấu trúc controller chuẩn** (dòng 43-54):
```java
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ApiResponse.of(authService.register(req));
    }
```
→ `@RequestMapping("/wallets")` cho `WalletController`, `@RequestMapping("/categories")` cho `CategoryController`.

**Lấy userId hiện tại** (dòng 76-77):
```java
UUID userId = SecurityContextUtil.currentUserId();
return ApiResponse.of(authService.getMe(userId));
```
Dùng y hệt ở mọi endpoint ví/danh mục cần biết "current user" — không truyền `userId` qua body/param, luôn lấy từ `SecurityContextUtil`.

**Gắn `@Idempotent` — CHỈ trên các POST-tạo-mới** theo D-11 (không đổi so với Phase 1), CONTEXT.md đã liệt kê rõ 4 endpoint: `POST /wallets`, `POST /wallets/transfer`, `POST /categories`, `POST /wallets/{id}/adjust-balance`. **Không** gắn lên PATCH/DELETE/reorder — tham khảo comment giải thích ở `AuthController.java` dòng 35-37 (dù ví dụ đó là loại trừ do lý do khác — D-11, khuôn suy luận giống nhau: chỉ POST-tạo-mới).

```java
@Idempotent
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public ApiResponse<WalletResponse> create(@Valid @RequestBody CreateWalletRequest req) {
    UUID userId = SecurityContextUtil.currentUserId();
    return ApiResponse.of(walletService.create(userId, req));
}
```
(annotation `@Idempotent` chưa từng thấy dùng trực tiếp trên method Phase 1 — vì D-11 loại trừ toàn bộ `/auth/**` — nhưng cơ chế AOP đã verify hoạt động qua `IdempotencyAspectIntegrationTest`, chỉ cần gắn annotation là đủ, không cần code gì thêm ở tầng Aspect.)

---

### `wallet/dto/*.java`, `category/dto/*.java` (DTO, request-response)

**Analog:** `src/main/java/com/datn/financeapp/auth/dto/RegisterRequest.java`, `UserDetailDto.java`

**Record + validation annotation trên field** (toàn bộ `RegisterRequest.java`):
```java
public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank
        @Size(min = 8, message = "Mật khẩu tối thiểu 8 ký tự.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                message = "Mật khẩu phải có ít nhất một chữ và một số.")
        String password,
        @NotBlank @Size(min = 2, max = 100) String fullName) {
}
```
→ ví dụ `CreateWalletRequest`:
```java
public record CreateWalletRequest(
        @NotBlank @Size(min = 1, max = 50) String name,
        @NotBlank String type,
        @NotNull Long initialBalance,
        Boolean includeInTotal,
        String icon,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String color,
        UUID groupId) {
}
```

**Response DTO record đơn giản** (toàn bộ `UserDetailDto.java`):
```java
public record UserDetailDto(
        UUID id,
        String email,
        String fullName,
        String avatarUrl,
        String plan,
        Instant createdAt,
        Instant lastLoginAt,
        UserStatsDto stats) {
}
```
Không cần `@JsonProperty` thủ công cho `snake_case` — `application.yml` dòng 8 đã cấu hình toàn cục:
```yaml
spring.jackson.property-naming-strategy: SNAKE_CASE
```
Record field `currentBalance` tự động serialize thành `current_balance` trong JSON — **chỉ dùng `@JsonProperty` khi tên field Java không tự suy ra đúng snake_case** (hiếm khi cần ở DTO mới).

**`amount` luôn `Long`** (quy tắc bất biến #8 backend) — `CreateWalletRequest.initialBalance`, `TransferRequest.amount`, `AdjustBalanceRequest.actualBalance` đều phải khai `Long`, không `Double`.

---

### `category/repository/CategoryRepository.java` — gọi `fn_category_tree` (MỚI, chưa có analog)

**Không có analog trong Phase 1** — Phase 1 chưa từng gọi native SQL function nào (chỉ có `jdbcTemplate.queryForObject` thô ở `AuthService.getMe()` dòng 240-245, tham khảo cú pháp `JdbcTemplate` nếu muốn dùng cách đó thay vì repository).

**Đề xuất chỗ đặt:** `CategoryRepository` (Spring Data JPA interface), dùng native query gọi thẳng hàm SQL đã có sẵn `fn_category_tree(p_category_id UUID)` (định nghĩa ở `db/migration/V6__va_loi_bao_mat.sql` dòng 105-112, trả `TABLE(category_id UUID)`):

```java
@Query(value = "SELECT category_id FROM fn_category_tree(:categoryId)", nativeQuery = true)
List<UUID> findCategoryTree(@Param("categoryId") UUID categoryId);
```

Copy khuôn `@Query(nativeQuery = ...)` + `@Param` từ `RefreshTokenRepository` (dòng 24-26, dù đó là JPQL không phải native, cú pháp annotation giống nhau, chỉ thêm `nativeQuery = true`).

**Dùng lại ở Phase 3/4** đúng như CONTEXT.md nhấn mạnh — đặt method này ngay từ Phase 2 để không phải viết lại. Không tự viết điều kiện lọc cây danh mục ở nơi khác — luôn gọi qua method này (quy tắc bất biến #1 gốc + #6 backend).

**Mẫu JdbcTemplate thay thế** (nếu muốn tránh native `@Query` trả kiểu đơn giản), copy từ `AuthService.java` dòng 240-242:
```java
Long transactionCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM transactions WHERE user_id = ? AND NOT is_deleted",
        Long.class, userId);
```

---

### `category/entity/Category.java` — validate cây 2 cấp (MỚI, ràng buộc DB đã có sẵn)

**Không tự viết lại validate 2 cấp ở Java nếu DB đã chặn** — `db/migration/V1__nen_tang.sql` dòng 229-292 đã có trigger `fn_categories_validate` (chặn tạo cấp 3, chặn con khác `type` cha) và `fn_categories_block_demote` (chặn hạ cấp danh mục đang có con). Service **vẫn nên kiểm tra trước** để trả đúng mã lỗi nghiệp vụ (`MAX_DEPTH_EXCEEDED`, `TYPE_MISMATCH_WITH_PARENT`) thay vì để lộ lỗi DB constraint thô — nhưng **không cần tự implement lại toàn bộ logic đệ quy**, chỉ cần 1 lần `SELECT` cha rồi so sánh `parentCategoryId`/`type`, đúng thuật toán mô tả ở `api/03-DANH-MUC.md` dòng 178-182:
```
Nếu parent_category_id có giá trị:
    Lấy danh mục cha đó
    Nếu nó có parent_category_id khác rỗng → từ chối
```
Bắt exception PostgreSQL nếu người dùng lách qua được validate Service (race condition hiếm) — map sang `BusinessException` phù hợp trong catch, tương tự cách `GlobalExceptionHandler` xử lý `Exception.class` fallback (dòng 58-64) nhưng ở tầng Service cụ thể hơn nếu cần custom message.

---

## Shared Patterns

### Response envelope + BusinessException + GlobalExceptionHandler
**Source:** `src/main/java/com/datn/financeapp/common/response/ApiResponse.java`, `src/main/java/com/datn/financeapp/common/exception/BusinessException.java`, `src/main/java/com/datn/financeapp/common/exception/GlobalExceptionHandler.java`
**Apply to:** Toàn bộ controller/service Phase 2 — **dùng lại nguyên vẹn, không viết lại.**
```java
public record ApiResponse<T>(boolean success, T data) {
    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(true, data);
    }
}
```
```java
throw new BusinessException("WALLET_NAME_EXISTS", HttpStatus.CONFLICT.value(), "Đã có ví cùng tên.");
```

### Idempotency AOP + self-invocation trap
**Source:** `src/main/java/com/datn/financeapp/common/idempotency/Idempotent.java`, `IdempotencyAspect.java`, `IdempotencyTransactionHelper.java`
**Apply to:** `POST /wallets`, `POST /wallets/transfer`, `POST /categories`, `POST /wallets/{id}/adjust-balance`
```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {}
```
**Cạm bẫy self-invocation** (đã vấp ở Phase 1, ghi rõ trong `IdempotencyTransactionHelper.java` dòng 8-11): nếu Service Phase 2 có method `@Transactional` gọi method `@Transactional` khác **trong cùng class** (ví dụ `WalletService.transfer()` gọi `WalletService.adjustBalanceInternal()`), annotation thứ hai bị bỏ qua vì Spring AOP proxy không áp dụng cho self-invocation. Giải pháp: tách logic cần `@Transactional` riêng ra bean/component khác (copy khuôn `IdempotencyTransactionHelper`), **không** gọi method nội bộ cùng class khi cần transaction boundary riêng.

### Kiểm tra quyền trong SQL (CORE-05, D-27)
**Source:** Chưa có sẵn trong Phase 1 (User không có group) — nhưng `SecurityContextUtil` đã có sẵn để lấy `currentUser`.
**Apply to:** Mọi query `GET/PATCH/DELETE` ví và danh mục.
```java
public static UUID currentUserId() {
    Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    return UUID.fromString(principal.toString());
}
```
Kèm điều kiện quyền ngay trong `@Query` (native hoặc JPQL) như mô tả ở mục `WalletRepository` phía trên. Không có quyền → **404** (`BusinessException("NOT_FOUND", 404, ...)`), không phải 403 — trừ trường hợp `NOT_GROUP_MEMBER` (403, đã biết chắc tài nguyên tồn tại nhưng thiếu vai trò, ví dụ tạo ví chung khi không phải thành viên nhóm).

### Atomic balance update (không load-modify-save)
**Source:** Công thức mới, mẫu `@Modifying @Query` lấy từ `RefreshTokenRepository.revokeAllActiveForUser` (dòng 31-34).
**Apply to:** Mọi thay đổi `current_balance` (tạo giao dịch, transfer, adjust-balance).
```java
@Modifying
@Query("UPDATE Wallet w SET w.currentBalance = w.currentBalance + :delta WHERE w.id = :id")
int adjustBalance(@Param("id") UUID id, @Param("delta") long delta);
```

### `pom.xml` ép UTF-8 + UTC — giữ nguyên
**Source:** `pom.xml` (không đọc chi tiết, đã xác nhận qua CONTEXT.md) — không sửa, chỉ lưu ý khi thêm module mới không phá cấu hình này.

---

## Mẫu Test Testcontainers (D-28, D-23 kế thừa)

**Analog:** `src/test/java/com/datn/financeapp/auth/AuthRegisterLoginIntegrationTest.java` (toàn bộ file, dòng 1-220)

**Không có base class dùng chung ở Phase 1** — mỗi file test tự khai báo `@Testcontainers` + `@Container static PostgreSQLContainer<?> postgres` riêng. Tiếp tục theo đúng khuôn này ở Phase 2 (không tự tạo abstract base class trừ khi thấy trùng lặp quá nhiều — Claude's Discretion).

**Khung khai báo Testcontainers** (dòng 48-61):
```java
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthRegisterLoginIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LWZvci1hdXRoLXJlZ2lzdGVyLXRlc3QtMzJi");
    }
```
(`jwt.secret` chỉ cần nếu test đi qua `Authorization: Bearer` thật — Phase 2 nên copy đoạn này vì mọi endpoint ví/danh mục đều yêu cầu JWT hợp lệ, khác `/auth/register|login` là `permitAll`.)

**Vô hiệu hoá RateLimitFilter khi test gọi API nhiều lần** (dòng 63-80) — copy nếu test Phase 2 gọi cùng endpoint quá 120 lần/phút (ví dụ test tạo nhiều danh mục liên tiếp trong 1 test method).

**`@BeforeEach cleanTables()`** (dòng 97-102) — copy khuôn dọn bảng theo đúng thứ tự FK (con trước, cha sau):
```java
@BeforeEach
void cleanTables() {
    refreshTokenRepository.deleteAll();
    walletMinimalRepository.deleteAll();
    userRepository.deleteAll();
}
```
Phase 2 cần thêm thứ tự dọn: giao dịch (nếu setup dữ liệu test transfer) → ví → danh mục con → danh mục cha → user, tuỳ FK thật.

**Mẫu test HTTP qua MockMvc** (dòng 104-134) — POST + assert JSON path bằng `jsonPath("$.data.xxx")`, `jsonPath("$.error.code")`. Copy nguyên khuôn assert response envelope.

### Test đồng thời (D-28 mục 1) — KHÔNG có analog Phase 1

Phase 1 không có test đa luồng nào. Đề xuất mẫu mới dùng `ExecutorService`/`CountDownLatch` để hai luồng cùng gọi `POST /wallets/transfer` (hoặc cùng update 1 ví) song song, rồi assert số dư cuối đúng tuyệt đối — không copy được từ đâu, cần viết từ đầu dựa trên pattern JUnit 5 concurrency thông thường (không thuộc phạm vi pattern-mapping của codebase hiện có).

### Mẫu `@WebMvcTest` slice với `excludeFilters`

**Analog:** `src/test/java/com/datn/financeapp/common/exception/GlobalExceptionHandlerTest.java` (dòng 25-38)
```java
@WebMvcTest(
        excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
                type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
                classes = {
                        com.datn.financeapp.common.security.SecurityConfig.class,
                        com.datn.financeapp.common.security.JwtAuthFilter.class,
                        com.datn.financeapp.common.ratelimit.RateLimitFilter.class,
                        com.datn.financeapp.auth.controller.AuthController.class
                }))
```
Nếu Phase 2 viết `@WebMvcTest` slice cho `WalletController`/`CategoryController` riêng (thay vì full `@SpringBootTest`), thêm chính controller Phase 2 vào `excludeFilters` nếu không muốn nó bị component-scan kéo theo Service thật — hoặc dùng `@WebMvcTest(controllers = WalletController.class)` để giới hạn phạm vi ngay từ đầu (cách gọn hơn `excludeFilters`, GlobalExceptionHandlerTest chọn `excludeFilters` vì dùng `TestController` nội bộ chứ không test controller thật).

---

## Không có analog rõ ràng trong Phase 1

| File/Pattern | Role | Data Flow | Lý do | Đề xuất |
|---|---|---|---|---|
| `category/mapper/CategoryMapper.java`, `wallet/mapper/WalletMapper.java` (nếu chọn MapStruct) | mapper | transform | Phase 1 map DTO thủ công trong Service (`new UserDetailDto(user.getId(), ...)`), chưa dùng MapStruct dù dependency đã có sẵn trong `pom.xml` (dòng 28, 110-112, 174-176) | Claude's Discretion theo CONTEXT.md — nếu chọn MapStruct, cấu hình `@Mapper(componentModel = "spring")`, không cần override annotation processor path (đã cấu hình sẵn ở `pom.xml`) |
| Native SQL function call (`fn_category_tree`) | repository method | transform/read | Phase 1 chưa gọi native SQL function nào (chỉ có `jdbcTemplate.queryForObject` COUNT thô) | Xem mẫu đề xuất ở mục `CategoryRepository` phía trên — `@Query(value="SELECT category_id FROM fn_category_tree(:id)", nativeQuery=true)` |
| Test đồng thời (concurrent transfer, D-28 mục 1) | test | concurrency | Phase 1 không có test đa luồng | Viết mới bằng `ExecutorService` + `CountDownLatch`, không có pattern codebase để copy |
| Entity `GroupMember` cho vế quyền D-27 | entity | CRUD | Group thuộc Phase 5, chưa có entity JPA | Dùng native SQL subquery thẳng vào bảng `group_members` (đã tồn tại từ `V1`) thay vì JPQL entity reference — không cần tạo entity `GroupMember` ở Phase 2 |

---

## Metadata

**Analog search scope:** `source/server/src/main/java/com/datn/financeapp/{auth,common}/**`, `source/server/src/test/java/com/datn/financeapp/**`
**Files scanned:** 60 file Java Phase 1 (34 main + 26 test), `pom.xml`, `application.yml`, `db/migration/V1__nen_tang.sql`, `V6__va_loi_bao_mat.sql`, `V8__dieu_chinh_so_du.sql`, `api/02-VI.md`, `api/03-DANH-MUC.md`
**Pattern extraction date:** 2026-08-23

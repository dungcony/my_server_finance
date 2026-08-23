# Phase 3: Giao dịch - Bản đồ Pattern

**Mapped:** 2026-08-23
**Files analyzed:** 15 file mới + 4 file phải sửa (di sản Phase 2)
**Analogs found:** 19 / 19

## Phân loại file

| File mới/sửa | Vai trò | Luồng dữ liệu | Analog gần nhất | Chất lượng khớp |
|---|---|---|---|---|
| `transaction/entity/Transaction.java` | entity | CRUD | `wallet/entity/Wallet.java` | exact (cùng convention @Entity/@Builder) |
| `transaction/repository/TransactionRepository.java` | repository | CRUD + query quyền | `wallet/repository/WalletRepository.java` | exact |
| `transaction/service/TransactionWriter.java` | service (bean mỏng) | CRUD (ghi ví + txn) | `common/idempotency/IdempotencyTransactionHelper.java` | exact (mẫu bean riêng chống self-invocation) |
| `transaction/service/TransactionService.java` | service | CRUD + request-response | `wallet/service/WalletTransferService.java` | exact (khoá ví, 3 bước, atomic update) |
| `transaction/controller/TransactionController.java` | controller | request-response | `wallet/controller/WalletController.java`, `category/controller/CategoryController.java` | exact |
| `transaction/dto/CreateTransactionRequest.java` | dto | request-response | `wallet/dto/CreateWalletRequest.java` | exact |
| `transaction/dto/UpdateTransactionRequest.java` | dto | request-response | `wallet/dto/UpdateWalletRequest.java` | exact |
| `transaction/dto/TransactionResponse.java` | dto | request-response | `wallet/dto/WalletResponse.java` | exact |
| `transaction/dto/TransactionDetailResponse.java` | dto | request-response | `wallet/dto/WalletDetailResponse.java` | role-match |
| `transaction/dto/BulkCreateTransactionRequest.java` / `BulkCreateTransactionResponse.java` | dto | batch | — (không có analog batch trong Phase 1+2) | không có |
| `transaction/dto/TransactionSummaryResponse.java` | dto | transform/aggregate | `wallet/dto/WalletSummaryResponse.java` | role-match |
| **`wallet/service/WalletTransferService.java`** (SỬA) | service | CRUD | chính nó — 2 điểm INSERT cần thay bằng gọi `TransactionWriter` | n/a — sửa tại chỗ |
| **`wallet/dto/WalletResponse.java`** (SỬA) | dto | request-response | chính nó — thêm field `projectedBalance`, sửa mapping | n/a — sửa tại chỗ |
| **`wallet/dto/WalletDetailResponse.java`** (SỬA) | dto | request-response | chính nó — thêm field `projectedBalance`, sửa mapping | n/a — sửa tại chỗ |
| `transaction/*IntegrationTest.java` (nhiều file) | test | integration | `wallet/WalletCrudIntegrationTest.java`, `wallet/WalletTransferConcurrencyTest.java` | exact |
| `common/exception/GlobalExceptionHandlerTest.java` (SỬA) | test | slice test | chính nó — thêm `TransactionController.class` vào `excludeFilters` | n/a — sửa tại chỗ |

## Pattern Assignments

### `transaction/entity/Transaction.java` (entity, CRUD)

**Analog:** `src/main/java/com/datn/financeapp/wallet/entity/Wallet.java` (toàn bộ 72 dòng)

**Pattern:** `@Entity @Table @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`, `@Id` kiểu `UUID` tự sinh ở service (không `@GeneratedValue`), mọi cột map tường minh bằng `@Column(name = "...")`, comment Javadoc trích trực tiếp constraint DB gốc bằng tiếng Việt.

```java
@Entity
@Table(name = "wallets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Wallet {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;
    ...
    @Column(name = "current_balance", nullable = false)
    private Long currentBalance;
    ...
    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
```

**Áp dụng cho `Transaction.java`:** map đúng theo DDL thật `db/migration/V2__giao_dich.sql` (trích nguyên văn ở mục "DDL bảng transactions" bên dưới) — chú ý cột `date` là `LocalDate` (kiểu `DATE`, không timezone — CLAUDE.md backend §7 + roadmap Phase 3 "amount `Long`, ngày `LocalDate`"), `amount` là `Long`, `type`/`source` là `String` (không enum Java, để khớp CHECK constraint dạng chuỗi như `Wallet.type`). Cột `updated_at` do trigger `trg_transactions_validate` tự set (`NEW.updated_at := now()`) — vẫn nên map field để đọc lại, nhưng đừng tự gán giá trị khi insert/update qua JPA nếu dùng native `INSERT`; nếu dùng `save()` qua Hibernate, set tường minh giống các entity khác trong dự án (xem `Wallet.createdAt` luôn set từ Service).

---

### `transaction/repository/TransactionRepository.java` (repository, CRUD)

**Analog 1 — điều kiện quyền + query có tham số tường minh:** `src/main/java/com/datn/financeapp/wallet/repository/WalletRepository.java` (toàn bộ 91 dòng)

```java
@Query(
        value = "SELECT * FROM wallets w WHERE w.id = :id AND NOT w.is_deleted "
                + "AND (w.user_id = :currentUser OR w.group_id IN "
                + "(SELECT group_id FROM group_members WHERE user_id = :currentUser AND is_active))",
        nativeQuery = true)
Optional<Wallet> findByIdForUser(@Param("id") UUID id, @Param("currentUser") UUID currentUser);
```
```java
@Modifying
@Query("UPDATE Wallet w SET w.currentBalance = w.currentBalance + :delta WHERE w.id = :id")
int adjustBalance(@Param("id") UUID id, @Param("delta") long delta);
```
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT w FROM Wallet w WHERE w.id = :id AND NOT w.isDeleted")
Optional<Wallet> findByIdForUpdate(@Param("id") UUID id);
```

**Áp dụng cho `TransactionRepository`:** copy nguyên khuôn `findByIdForUser` (điều kiện quyền `user_id = :currentUser OR group_id IN (...)` — cho transaction thì quyền suy từ `wallet_id`/`user_id` trực tiếp trên `transactions.user_id`, D-27 vẫn áp dụng). Cần thêm `findByIdForUpdate` tương tự `PESSIMISTIC_WRITE` nếu sửa/xoá giao dịch cần khoá bản ghi transaction (không bắt buộc — D-33 chỉ khoá **ví**, không khoá `transactions`).

**Analog 2 — bọc hàm SQL có sẵn dùng lại cho TXN-08:** `src/main/java/com/datn/financeapp/category/repository/CategoryRepository.java` dòng 17-25

```java
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    @Query(value = "SELECT category_id FROM fn_category_tree(:categoryId)", nativeQuery = true)
    List<UUID> findCategoryTree(@Param("categoryId") UUID categoryId);
```

**Áp dụng:** `TransactionRepository` (hoặc `TransactionService`) gọi `categoryRepository.findCategoryTree(categoryId)` rồi build điều kiện `category_id IN (:tree)` khi lọc theo danh mục cha (TXN-08) — **không viết lại `fn_category_tree` ở nơi khác**, `CategoryRepository` đã tồn tại sẵn và Javadoc của nó ghi thẳng "Phase 3" là điểm nối.

**Analog 3 — SQL trừ ngược (D-36), bọc thành 1 native query dùng chung:** trích nguyên văn từ `db/README.md` mục "Suy ra số dư ví theo mốc thời gian" (dòng 239-255):

```sql
-- Số dư ví tại mốc :as_of (mặc định CURRENT_DATE)
SELECT w.current_balance
       - COALESCE(SUM(CASE
             WHEN t.type = 'income'                          THEN  t.amount
             WHEN t.type = 'expense'                         THEN -t.amount
             WHEN t.type = 'transfer' AND t.wallet_id = w.id THEN -t.amount
         END), 0)
       - COALESCE((SELECT SUM(amount) FROM transactions
                   WHERE destination_wallet_id = w.id
                     AND date > :as_of AND NOT is_deleted), 0) AS balance_as_of
FROM wallets w
LEFT JOIN transactions t
       ON t.wallet_id = w.id AND t.date > :as_of AND NOT t.is_deleted
WHERE w.id = :wallet_id
GROUP BY w.id, w.current_balance;
```

**Áp dụng:** bọc câu này thành 1 method dùng chung (repository của `wallet` hoặc `transaction`, theo Claude's Discretion) — dùng cho D-37 khi map `WalletResponse.currentBalance` (trường API, đã trừ ngược) và tính `projectedBalance`. `projected_balance` API field = cột `wallets.current_balance` thô, chỉ trả nếu `EXISTS (SELECT 1 FROM transactions WHERE wallet_id=:id AND date > CURRENT_DATE AND NOT is_deleted)`.

---

### `transaction/service/TransactionWriter.java` (service — bean riêng, D-31)

**Analog — mẫu tham chiếu quan trọng nhất:** `src/main/java/com/datn/financeapp/common/idempotency/IdempotencyTransactionHelper.java` (toàn bộ 33 dòng)

```java
/**
 * Bọc riêng các thao tác ghi {@code idempotency_keys} trong {@code @Transactional} — tách khỏi
 * {@link IdempotencyAspect} vì self-invocation trong cùng bean {@code @Aspect} không đi qua proxy
 * Spring AOP, khiến {@code @Transactional} bị bỏ qua nếu gọi trực tiếp method nội bộ.
 */
@Component
@RequiredArgsConstructor
public class IdempotencyTransactionHelper {

    private final IdempotencyKeyRepository repository;

    @Transactional
    public int tryInsertProcessing(String key, UUID userId, String endpoint) {
        return repository.tryInsertProcessing(key, userId, endpoint);
    }

    @Transactional
    public void deleteRecord(String key, UUID userId, String endpoint) {
        repository.deleteByIdempotencyKeyAndUserIdAndEndpoint(key, userId, endpoint);
    }
    ...
}
```

**Pattern cần sao chép cho `TransactionWriter`:**
1. `@Component` (hoặc `@Service` — cả hai dùng được, dự án đã dùng `@Service` cho service nghiệp vụ; `TransactionWriter` không phải "nghiệp vụ" thuần nên `@Component` hợp lý hơn nhưng cả hai đều acceptable) + `@RequiredArgsConstructor`.
2. Mỗi public method tự mang `@Transactional` riêng — **không đặt `@Transactional` cấp class**, và **không gọi method khác cùng bean qua `this.xxx()`** (đó chính là self-invocation phá `@Transactional`).
3. Nhận đủ tham số nguyên thuỷ (`walletId`, `destinationWalletId`, `categoryId`, `type`, `amount`, `date`, `note`, `source`, `countsInReport`, `userId`...) — **không nhận DTO request/response của nghiệp vụ gọi nó** (D-31: "không biết gì về nghiệp vụ gọi nó").
4. Bên trong: build entity `Transaction`, `repository.save(...)`, rồi gọi `walletRepository.adjustBalance(walletId, delta)` (atomic UPDATE, xem pattern ở `WalletRepository.adjustBalance` phía trên) — theo đúng thứ tự ghi-bản-ghi-rồi-cập-nhật-ví như `WalletTransferService.transfer()` đang làm thủ công.
5. Trả về đủ thông tin caller cần (ít nhất: `transactionId`, `newBalance` của (các) ví liên quan) để 3 nơi gọi (`TransactionService`, `WalletTransferService`, Phase 4) dùng lại mà không phải query lại.

---

### `transaction/service/TransactionService.java` (service, request-response + CRUD)

**Analog:** `src/main/java/com/datn/financeapp/wallet/service/WalletTransferService.java` (toàn bộ 219 dòng, đặc biệt `lockAndCheckOwnership` dòng 208-217 và `transfer()` dòng 45-102)

**Pattern khoá ví theo `UUID.compareTo()` chống deadlock — copy nguyên khuôn cho D-33 (mở rộng 2→4 ví):**

```java
// Khoá theo thứ tự cố định (nhỏ trước, lớn sau) bất kể vai trò nguồn/đích — chống
// deadlock khi hai giao dịch A->B và B->A chạy đồng thời (T-02-12).
UUID first = sourceId.compareTo(destinationId) < 0 ? sourceId : destinationId;
UUID second = sourceId.compareTo(destinationId) < 0 ? destinationId : sourceId;

Wallet firstWallet = lockAndCheckOwnership(first, userId);
Wallet secondWallet = lockAndCheckOwnership(second, userId);
```

```java
/**
 * Lock ví theo id rồi kiểm tra quyền D-27 (Phase 2 chỉ có nhánh cá nhân, nhóm mãi Phase 5).
 * Không tồn tại hoặc không thuộc quyền -> NOT_FOUND 404, không tiết lộ ví có tồn tại hay
 * không (T-02-13).
 */
private Wallet lockAndCheckOwnership(UUID walletId, UUID userId) {
    Wallet wallet = walletRepository
            .findByIdForUpdate(walletId)
            .orElseThrow(() -> new BusinessException(
                    "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy ví."));
    if (!userId.equals(wallet.getUserId())) {
        throw new BusinessException("NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy ví.");
    }
    return wallet;
}
```

**Áp dụng cho D-33:** khi sửa giao dịch có thể chạm tối đa 4 ví (ví nguồn cũ, ví đích cũ, ví nguồn mới, ví đích mới — trùng lặp phải loại bỏ trước khi khoá), gom **toàn bộ UUID liên quan vào một `List<UUID>`, loại trùng, `sort()` theo `UUID.compareTo()` rồi khoá tuần tự** — mở rộng đúng khuôn 2-ví ở trên, không đổi thuật toán.

**Pattern trình tự 3 bước sửa giao dịch (CLAUDE.md §4)** — chưa có analog y hệt trong Phase 1+2 (transfer chỉ tạo mới, không có "sửa"), nhưng cấu trúc logic suy ra trực tiếp từ `transfer()`:
```java
@Transactional
public TransferResponse transfer(UUID userId, TransferRequest req) {
    ...
    Wallet sourceWallet = ...; Wallet destinationWallet = ...;
    ...
    walletRepository.adjustBalance(sourceId, -amount);
    walletRepository.adjustBalance(destinationId, amount);
    ...
}
```
Cho `updateTransaction`: bước 1 hoàn tác (gọi `adjustBalance` với dấu ngược ảnh hưởng CŨ trên (các) ví CŨ) → bước 2 `transactionRepository.save()` giá trị MỚI → bước 3 áp dụng MỚI (`adjustBalance` với ảnh hưởng MỚI trên (các) ví MỚI) — toàn bộ trong 1 `@Transactional` của `TransactionService`, gọi `TransactionWriter` hoặc trực tiếp `walletRepository.adjustBalance` tuỳ thiết kế cuối (Claude's Discretion).

**Pattern lỗi nghiệp vụ + validate tường minh trước khi build câu SQL** (dòng 122-127 và 114-117 của `WalletTransferService`):
```java
if (Boolean.TRUE.equals(req.failIfInsufficient()) && sourceWallet.getCurrentBalance() < amount) {
    throw new BusinessException(
            "INSUFFICIENT_BALANCE",
            HttpStatus.UNPROCESSABLE_ENTITY.value(),
            "Số dư ví không đủ để thực hiện giao dịch này.",
            Map.of("current_balance", sourceWallet.getCurrentBalance(), "requested_amount", amount));
}
```

**Pattern chặn xoá gắn debt_payments (D-32)** — dùng `EXISTS` đơn giản qua `JdbcTemplate`, cùng khuôn với `WalletService.delete()` dòng 224-234 (đếm giao dịch trước khi xoá ví):
```java
Long transactionCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM transactions WHERE (wallet_id = ? OR destination_wallet_id = ?) AND NOT is_deleted",
        Long.class, walletId, walletId);
boolean hasTransactions = transactionCount != null && transactionCount > 0;

if (hasTransactions && !deleteTransactions) {
    throw new BusinessException(
            "WALLET_HAS_TRANSACTIONS", HttpStatus.CONFLICT.value(), "Ví còn giao dịch, cần xác nhận xoá kèm giao dịch.");
}
```
Áp dụng cho D-32: `EXISTS(SELECT 1 FROM debt_payments WHERE transaction_id = :id)` → nếu true, `throw new BusinessException("TRANSACTION_LINKED_TO_DEBT", HttpStatus.CONFLICT.value(), "...")`.

**Pattern xoá mềm idempotent (CORE-06)** — copy nguyên khuôn từ `WalletService.delete()` dòng 205-215:
```java
Wallet wallet = walletRepository
        .findByIdForUserIncludingDeleted(walletId, userId)
        .orElseThrow(() -> new BusinessException("NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy ví."));

if (Boolean.TRUE.equals(wallet.getIsDeleted())) {
    // Đã xoá mềm từ trước — idempotent, không làm gì thêm, không ném lỗi.
    return;
}
```

---

### `transaction/controller/TransactionController.java` (controller, request-response)

**Analog:** `src/main/java/com/datn/financeapp/wallet/controller/WalletController.java` (toàn bộ 127 dòng) + `src/main/java/com/datn/financeapp/category/controller/CategoryController.java` (toàn bộ 96 dòng)

**Imports pattern:**
```java
import com.datn.financeapp.common.idempotency.Idempotent;
import com.datn.financeapp.common.response.ApiResponse;
import com.datn.financeapp.common.security.SecurityContextUtil;
import jakarta.validation.Valid;
...
@RestController
@RequestMapping("/wallets")
@RequiredArgsConstructor
public class WalletController {
```

**Core pattern — lấy user hiện tại + gọi service + trả `ApiResponse`:**
```java
@PostMapping
@Idempotent
@ResponseStatus(HttpStatus.CREATED)
public ApiResponse<WalletResponse> create(@Valid @RequestBody CreateWalletRequest req) {
    UUID userId = SecurityContextUtil.currentUserId();
    return ApiResponse.of(walletService.create(userId, req));
}
```

**Áp dụng cho `TransactionController`:**
- `POST /transactions` (tạo đơn lẻ) và `POST /transactions/bulk` (D-04) đều gắn `@Idempotent` + `@ResponseStatus(HttpStatus.CREATED)` — theo D-11 (chỉ POST-tạo-mới) và D-35 (1 key cho cả lô bulk).
- `PUT /transactions/{id}` (sửa), `DELETE /transactions/{id}` (xoá), `POST /transactions/{id}/duplicate` (nhân bản) — cân nhắc `@Idempotent` cho `duplicate` (là POST-tạo-mới thật sự); PUT/DELETE **không** gắn, cùng lý do `WalletController.update`/`delete` không gắn.
- Không tự bịa route — theo đúng `api/04-GIAO-DICH.md`, không phỏng đoán path.

---

### `transaction/dto/*` (dto, request-response)

**Analog Create:** `src/main/java/com/datn/financeapp/wallet/dto/CreateWalletRequest.java` (toàn bộ 18 dòng)
```java
public record CreateWalletRequest(
        @NotBlank @Size(min = 1, max = 50) String name,
        @NotBlank @Pattern(regexp = "^(cash|bank|e_wallet|credit_card)$") String type,
        @NotNull Long initialBalance,
        Boolean includeInTotal,
        String icon,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String color,
        UUID groupId) {
}
```

**Pattern cần sao chép:** `record` bất biến, validation qua annotation `jakarta.validation` ngay trên field, `@Pattern` cho enum-như-string (khớp CHECK constraint DB `ck_txn_type`, `ck_txn_source`), field tên `camelCase` — Jackson tự chuyển `snake_case` nhờ `spring.jackson.property-naming-strategy=SNAKE_CASE` toàn cục (không cần `@JsonProperty` thủ công, khác `TestDto` trong test dùng `@JsonProperty` tường minh vì đó chỉ là test-only DTO không đi qua config toàn cục... thực ra vẫn có global config áp dụng, nhưng cứ theo mẫu Wallet/Category DTO chính là chuẩn).

**Áp dụng cho `CreateTransactionRequest`:** validate có điều kiện theo `type` (category bắt buộc/rỗng, destination bắt buộc/rỗng theo `ck_txn_shape`) — Bean Validation annotation đơn thuần không validate chéo-field được, nên cần custom validate trong Service (giống cách `WalletTransferService.transfer()` tự check `sourceId.equals(destinationId)` bằng `if` + `BusinessException`, không dựa vào annotation).

**Analog Response 2 tầng (list vs detail):** `WalletResponse` (18 field cơ bản) vs `WalletDetailResponse` (thêm `initialBalance` + `stats`) — áp dụng tương tự cho `TransactionResponse` (list) vs `TransactionDetailResponse` (thêm thông tin category/wallet đầy đủ để tránh N+1 khi hiển thị chi tiết).

---

### File PHẢI SỬA (di sản Phase 2) — D-30

#### `wallet/service/WalletTransferService.java` — 2 đoạn INSERT trực tiếp cần thay bằng gọi `TransactionWriter`

**Đoạn 1 — `transfer()`, dòng 78-87 (nguyên văn hiện tại):**
```java
LocalDate date = req.date() != null ? req.date() : LocalDate.now();
UUID transactionId = UUID.randomUUID();

jdbcTemplate.update(
        "INSERT INTO transactions (id, user_id, wallet_id, destination_wallet_id, category_id, "
                + "type, amount, date, note, source, counts_in_report, is_deleted, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, NULL, 'transfer', ?, ?, ?, 'manual', TRUE, FALSE, now(), now())",
        transactionId, userId, sourceId, destinationId, amount, date, req.note());

walletRepository.adjustBalance(sourceId, -amount);
walletRepository.adjustBalance(destinationId, amount);
```

**Đoạn 2 — `adjustBalance()`, dòng 133-151 (nguyên văn hiện tại):**
```java
UUID categoryId = jdbcTemplate.queryForObject(
        "SELECT c.id FROM categories c "
                + "JOIN category_groups g ON g.id = c.category_group_id "
                + "JOIN icons i ON i.id = c.icon_id "
                + "WHERE g.name = 'Khác' AND i.code = 'vi_tien' AND c.type = ? AND c.user_id IS NULL",
        UUID.class, type);
if (categoryId == null) {
    throw new IllegalStateException(
            "Thiếu danh mục hệ thống 'Cập nhật số dư' (type=" + type + ") — kiểm tra seed V8.");
}

UUID transactionId = UUID.randomUUID();
jdbcTemplate.update(
        "INSERT INTO transactions (id, user_id, wallet_id, destination_wallet_id, category_id, "
                + "type, amount, date, note, source, counts_in_report, is_deleted, created_at, updated_at) "
                + "VALUES (?, ?, ?, NULL, ?, ?, ?, CURRENT_DATE, ?, 'adjustment', ?, FALSE, now(), now())",
        transactionId, userId, walletId, categoryId, type, amount, req.note(), countsInReport);

walletRepository.adjustBalance(walletId, difference);
```

**Việc cần làm:** thay cả 2 đoạn `jdbcTemplate.update("INSERT...")` + `walletRepository.adjustBalance(...)` bằng một lệnh gọi `transactionWriter.write(...)` (tên method tuỳ thiết kế `TransactionWriter`), truyền đủ tham số `source` (`'transfer'` implicit qua `type`, hoặc `'manual'`/`'adjustment'`), `countsInReport` (đoạn 1 luôn `TRUE`, đoạn 2 do user chọn), `categoryId` (đoạn 1 `NULL`, đoạn 2 tra danh mục hệ thống — logic tra danh mục hệ thống V8 **giữ nguyên trong `WalletTransferService`** hoặc chuyển vào `TransactionWriter`, tuỳ điểm quyết Claude's Discretion nhưng phải chỉ viết 1 lần). Sau khi sửa, `WalletTransferService` không còn `jdbcTemplate.update("INSERT INTO transactions...")` nào — chỉ còn các câu `SELECT`/`adjustBalance` hợp lệ khác (như trong `reconcile()`).

**Bắt buộc:** chạy lại toàn bộ 77 test Phase 1+2 (`WalletCrudIntegrationTest`, `WalletTransferIntegrationTest`, `WalletTransferConcurrencyTest`, `WalletAdjustBalanceIntegrationTest`, `WalletAccessControlIntegrationTest`, `CategoryTreeIntegrationTest`, `CategoryAccessControlIntegrationTest`, + auth/common) — xanh hết mới sang task tiếp theo (D-30).

#### `wallet/dto/WalletResponse.java` — nguyên văn hiện tại (19 dòng)

```java
package com.datn.financeapp.wallet.dto;

import java.time.Instant;
import java.util.UUID;

/** Một phần tử của GET /wallets (api/02-VI.md mục 1). */
public record WalletResponse(
        UUID id,
        String name,
        String type,
        Long currentBalance,
        Boolean includeInTotal,
        boolean isShared,
        UUID groupId,
        String icon,
        String color,
        Integer sortOrder,
        Instant createdAt) {
}
```

**Việc cần làm (D-37/TXN-09):** thêm field `Long projectedBalance` (record component thêm vào cuối hoặc chỗ hợp lý), và sửa nơi map (`WalletService.toResponse()` dòng 279-292) — hiện đang map thẳng `wallet.getCurrentBalance()` vào field `currentBalance`:

```java
private WalletResponse toResponse(Wallet wallet) {
    return new WalletResponse(
            wallet.getId(),
            wallet.getName(),
            wallet.getType(),
            wallet.getCurrentBalance(),   // ← SAI theo D-37: đây là cột thô, chưa trừ ngược
            wallet.getIncludeInTotal(),
            wallet.getGroupId() != null,
            wallet.getGroupId(),
            wallet.getIcon(),
            wallet.getColor(),
            wallet.getSortOrder(),
            wallet.getCreatedAt());
}
```

Phải sửa thành: gọi query "trừ ngược" (xem SQL D-36 ở trên) để lấy `currentBalance` thật cho response, và chỉ set `projectedBalance = wallet.getCurrentBalance()` (cột thô) nếu ví có giao dịch tương lai — nếu không có thì để `projectedBalance = null` (record field null → Jackson bỏ hẳn trường nếu cấu hình `@JsonInclude(NON_NULL)`; kiểm tra cấu hình Jackson toàn cục có bật sẵn hay cần thêm annotation trên record).

#### `wallet/dto/WalletDetailResponse.java` — nguyên văn hiện tại (21 dòng)

```java
package com.datn.financeapp.wallet.dto;

import java.time.Instant;
import java.util.UUID;

/** GET /wallets/{id} (api/02-VI.md mục 3) — thêm initialBalance + stats so với WalletResponse. */
public record WalletDetailResponse(
        UUID id,
        String name,
        String type,
        Long initialBalance,
        Long currentBalance,
        Boolean includeInTotal,
        boolean isShared,
        UUID groupId,
        String icon,
        String color,
        Integer sortOrder,
        WalletStatsDto stats,
        Instant createdAt) {
}
```

**Việc cần làm:** cùng thay đổi như `WalletResponse` — thêm `projectedBalance`, sửa mapping ở `WalletService.detail()` dòng 89-112 (hiện `wallet.getCurrentBalance()` map thẳng ở dòng 103).

---

### Test — analog integration test + `GlobalExceptionHandlerTest`

**Analog Testcontainers PostgreSQL:** `src/test/java/com/datn/financeapp/wallet/WalletCrudIntegrationTest.java` (dòng 1-100 đã trích) — pattern:
```java
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WalletCrudIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "...base64...");
    }

    @TestConfiguration
    static class NoRateLimitConfig { ... }  // vô hiệu hoá RateLimitFilter trong test

    @BeforeEach
    void cleanTables() {
        // Thứ tự xoá theo FK: transactions trước wallets/users vì FK RESTRICT
        jdbcTemplate.update("DELETE FROM transactions");
        refreshTokenRepository.deleteAll();
        walletRepository.deleteAll();
        ...
    }
```

**Áp dụng cho test Phase 3:** `TransactionCrudIntegrationTest`, `TransactionUpdateAtomicityIntegrationTest` (test rủi ro #1), `TransactionBulkIntegrationTest` (test rủi ro #2, D-34/D-35a), `TransactionDeleteDebtLinkIntegrationTest` (test rủi ro #3, D-32), `WalletFutureBalanceIntegrationTest` hoặc mở rộng test ví có sẵn (test rủi ro #4, D-37), `TransactionCategoryTreeFilterIntegrationTest` (test rủi ro #5, TXN-08). `cleanTables()` cần thêm dọn `debt_payments` nếu test đụng D-32 (test rủi ro #3) — vì `debt_payments.transaction_id` có `ON DELETE RESTRICT`, phải xoá `debt_payments` trước `transactions` nếu bảng đó đã tồn tại trong DB test (V4 tạo bảng `debts`/`debt_payments` sẵn dù Phase 4 mới code nghiệp vụ — bảng đã có từ migration).

**Analog concurrency/atomicity test:** `src/test/java/com/datn/financeapp/wallet/WalletTransferConcurrencyTest.java` (toàn bộ 143 dòng) — dùng `ExecutorService` + `CountDownLatch` để chứng minh không lost-update. Copy khuôn này cho test D-33 (sửa giao dịch đổi cả amount lẫn ví, kiểm tra ví cũ/mới đều đúng tuyệt đối — có thể là test tuần tự, không nhất thiết cần đa luồng vì rủi ro chính ở đây là logic 3-bước sai, không phải race condition; race condition dùng khuôn `@RepeatedTest(5)` + 2 `Future` nếu cần test khoá đồng thời).

**GlobalExceptionHandlerTest — `excludeFilters` hiện tại (dòng 25-43), PHẢI thêm `TransactionController.class`:**
```java
@WebMvcTest(
        excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
                type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
                classes = {
                        com.datn.financeapp.common.security.SecurityConfig.class,
                        com.datn.financeapp.common.security.JwtAuthFilter.class,
                        com.datn.financeapp.common.ratelimit.RateLimitFilter.class,
                        com.datn.financeapp.auth.controller.AuthController.class,
                        com.datn.financeapp.wallet.controller.WalletController.class,
                        com.datn.financeapp.category.controller.CategoryController.class
                        // Phase 3: THÊM com.datn.financeapp.transaction.controller.TransactionController.class
                }))
```
**Việc cần làm:** thêm `com.datn.financeapp.transaction.controller.TransactionController.class` vào mảng `classes = {...}` kèm comment theo đúng khuôn 2 dòng trước đó ("Phase 3 plan 03-0X: TransactionController mới thêm — cùng lý do loại trừ.").

---

## Shared Patterns

### Response format thống nhất
**Source:** `src/main/java/com/datn/financeapp/common/response/ApiResponse.java`
**Apply to:** mọi endpoint `TransactionController`
```java
public record ApiResponse<T>(boolean success, T data) {
    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(true, data);
    }
}
```

### Lỗi nghiệp vụ
**Source:** `src/main/java/com/datn/financeapp/common/exception/BusinessException.java`
**Apply to:** mọi lỗi nghiệp vụ trong `TransactionService`/`TransactionWriter` (`TRANSACTION_LINKED_TO_DEBT`, `CATEGORY_TYPE_MISMATCH` nếu cần bắt trigger exception, `INSUFFICIENT_BALANCE` nếu tái dùng cho transfer qua txn mới...)
```java
public class BusinessException extends RuntimeException {
    private final String code;
    private final int httpStatus;
    private final Object detail;
    public BusinessException(String code, int httpStatus, String message) { ... }
    public BusinessException(String code, int httpStatus, String message, Object detail) { ... }
}
```

### `@Idempotent` (D-35, dùng lại nguyên — không viết cơ chế mới)
**Source:** `src/main/java/com/datn/financeapp/common/idempotency/Idempotent.java` + `IdempotencyAspect.java`
**Apply to:** `POST /transactions`, `POST /transactions/bulk` (1 key cho cả lô), `POST /transactions/{id}/duplicate`
**Lưu ý kỹ thuật bắt buộc (D-35a):** `IdempotencyAspect` chạy AOP `@Around` bọc method controller — bên trong bulk handler, mỗi dòng phải tự mở 1 `@Transactional` riêng (qua bean `TransactionWriter` hoặc `REQUIRES_NEW`), **không để cả lô nằm trong 1 transaction của Idempotent aspect**. `@Idempotent` chỉ cache **kết quả cuối cùng** (response body sau khi toàn bộ lô xử lý xong), không ảnh hưởng transaction boundary của từng dòng.

### Atomic UPDATE số dư ví — không load-modify-save
**Source:** `src/main/java/com/datn/financeapp/wallet/repository/WalletRepository.java` dòng 88-90
```java
@Modifying
@Query("UPDATE Wallet w SET w.currentBalance = w.currentBalance + :delta WHERE w.id = :id")
int adjustBalance(@Param("id") UUID id, @Param("delta") long delta);
```
**Apply to:** `TransactionWriter`, `TransactionService` (sửa/xoá giao dịch) — dùng lại nguyên method này, không viết `UPDATE` mới.

### Khoá ví bi quan (`PESSIMISTIC_WRITE`) + chống deadlock qua `UUID.compareTo()`
**Source:** `WalletRepository.findByIdForUpdate` (dòng 80-82) + `WalletTransferService.transfer()` (dòng 55-61)
**Apply to:** `TransactionService` khi sửa giao dịch chạm nhiều ví (D-33, mở rộng 2→4 ví).

### `spring.jackson.property-naming-strategy=SNAKE_CASE` toàn cục
**Source:** cấu hình `application.yml`/`application.properties` (không cần đọc lại — đã xác nhận trong CONTEXT.md "Điểm nối")
**Apply to:** mọi DTO `record` mới trong `transaction/dto/` — field Java `camelCase`, JSON tự động `snake_case`, không cần `@JsonProperty` tường minh trừ trường hợp đặc biệt.

## No Analog Found

| File | Vai trò | Luồng dữ liệu | Lý do |
|---|---|---|---|
| `transaction/dto/BulkCreateTransactionRequest.java` / `BulkCreateTransactionResponse.java` | dto | batch | Phase 1+2 chưa có endpoint bulk nào — thiết kế mới theo đặc tả TXN-04 (`row_errors`, tối đa 50 dòng, D-34/D-35/D-35a). Tham khảo cấu trúc response dạng danh sách kết quả trộn thành công/lỗi — có thể lấy cảm hứng từ cấu trúc lỗi field validation của `GlobalExceptionHandler` (`error.fields`) cho hình dạng `row_errors`, nhưng đây là thiết kế mới, không phải copy nguyên |
| Query "trừ ngược" D-36 dùng chung | repository method | transform | Chưa tồn tại trong Phase 1+2 — SQL mẫu lấy nguyên văn từ `db/README.md`, cần viết mới thành `@Query native` trong `WalletRepository` hoặc `TransactionRepository` |

## Metadata

**Phạm vi tìm analog:** `src/main/java/com/datn/financeapp/{wallet,category,common,auth}/`, `src/test/java/com/datn/financeapp/{wallet,category,common}/`, `db/migration/V2,V4,V6,V8*.sql`, `db/README.md`
**Số file quét:** ~35 file Java + 4 file SQL migration + 1 README
**Ngày trích pattern:** 2026-08-23

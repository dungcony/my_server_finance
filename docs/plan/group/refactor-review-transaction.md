# Plan: Gọn hoá luồng duyệt giao dịch nhóm + chặn duyệt trùng

- **Phạm vi:** `GTransactionServiceImpl` (phần REVIEWING: `confirm`, `reject`, `bulkConfirm`, `bulkReject`)
- **Trạng thái:** ✅ ĐÃ LÀM (chưa commit; chưa chạy được test Testcontainers vì máy không có Docker)
- **Xác nhận:** `[x] PROCESS`
- **Quyết định đã chốt:** (1) bỏ `save`/`saveAll`; (2) làm Phần B; (3) chấp nhận `CONCURRENT_MODIFICATION` 409 toàn hệ thống; (4) không commit, để người dùng tự làm
- **Lệch so với plan:** test handler 409 đặt ở file mới `OptimisticLockHandlingTest` (MockMvc độc lập) thay vì thêm vào `GlobalExceptionHandlerTest`, vì file này đã hỏng sẵn ở HEAD (thiếu bean `RateLimitProperties`, cả 4 test lỗi nạp context)

Plan gồm **hai phần độc lập**, có thể làm và commit riêng:

- **Phần A** — Refactor giảm lặp (không đổi hành vi).
- **Phần B** — Thêm khoá lạc quan `@Version` để chặn duyệt một giao dịch hai lần (có đổi schema).

---

## Phần A — Refactor giảm lặp

### 1. File sẽ thay đổi

| File                                                                    | Thay đổi                                                            |
|-------------------------------------------------------------------------|---------------------------------------------------------------------|
| `src/main/java/.../group/service/impl/GTransactionServiceImpl.java`     | Gom code lặp phần REVIEWING thành private method; bỏ điều kiện thừa |
| `src/test/java/.../group/service/impl/GTransactionServiceImplTest.java` | Thêm unit test cho 4 hàm duyệt (hiện file chỉ có 1 test `delete`)   |

Không đụng: interface `GTransactionReviewService`, controller, repository, DTO → **API contract giữ nguyên**.

### 2. Lý do thay đổi

| #  | Vấn đề hiện tại                                                                                  | Vị trí                                  |
|----|--------------------------------------------------------------------------------------------------|-----------------------------------------|
| A1 | Kiểm tra quyền "chủ nhóm hoặc thủ quỹ" lặp 4 lần                                                 | dòng 211–214, 235–238, 252–255, 284–287 |
| A2 | `confirm`/`reject` lặp "tìm giao dịch + kiểm tra PENDING"                                        | dòng 216–218, 240–242                   |
| A3 | `bulkConfirm`/`bulkReject` lặp khối "rỗng → distinct → truy vấn → so số lượng" (9 dòng)          | dòng 257–266, 289–298                   |
| A4 | `txns.isEmpty() \|\|` thừa: `distinctIds` đã khác rỗng nên `txns` rỗng thì `size()` đã lệch      | dòng 265, 297                           |
| A5 | `save`/`saveAll` thừa: entity đang managed trong `@Transactional`, Hibernate tự flush lúc commit | dòng 228, 245, 277, 305                 |

Quy ước dự án: *"tách code lặp dài hơn 2 dòng thành private method"*.

### 3. Hướng giải quyết (code xem trước)

Phần public sau khi gom:

```java

@Override
public GroupTransactionDetailRes confirm(UUID operatorId, UUID groupId, UUID transactionId) {
    requireReviewer(groupId, operatorId);
    GTransaction txn = findPendingTransaction(transactionId, groupId);

    updateReviewStatus(txn, TransactionStatus.CONFIRMED, operatorId, Instant.now());
    publishFundChangeEvent(groupId, deltaOf(txn));

    return transactionHelper.buildDetailRes(txn);
}

@Override
public GroupTransactionDetailRes reject(UUID operatorId, UUID groupId, UUID transactionId) {
    requireReviewer(groupId, operatorId);
    GTransaction txn = findPendingTransaction(transactionId, groupId);

    updateReviewStatus(txn, TransactionStatus.REJECTED, operatorId, Instant.now());

    return transactionHelper.buildDetailRes(txn);
}

@Override
public int bulkConfirm(UUID operatorId, UUID groupId, GroupTransactionBulkReviewReq req) {
    requireReviewer(groupId, operatorId);
    List<GTransaction> txns = findPendingBatch(groupId, req);

    long totalDelta = 0L;
    Instant now = Instant.now();
    for (GTransaction t : txns) {
        updateReviewStatus(t, TransactionStatus.CONFIRMED, operatorId, now);
        totalDelta += deltaOf(t);
    }

    // cập nhật số dư quỹ đúng 1 lần cho cả lô
    publishFundChangeEvent(groupId, totalDelta);

    return txns.size();
}

@Override
public int bulkReject(UUID operatorId, UUID groupId, GroupTransactionBulkReviewReq req) {
    requireReviewer(groupId, operatorId);
    List<GTransaction> txns = findPendingBatch(groupId, req);

    Instant now = Instant.now();
    txns.forEach(t -> updateReviewStatus(t, TransactionStatus.REJECTED, operatorId, now));

    return txns.size();
}
```

Private method thêm vào mục `PRIVATE`:

```java
private void requireReviewer(UUID groupId, UUID operatorId) {
    MemberAuthInfo info = permissionValidator.verifyActiveMemberInGroupActive(groupId, operatorId);
    if (!info.isOwner() && !info.isTreasurer())
        throw new BusinessException(ErrorCode.FORBIDDEN_TREASURER_REQUIRED);
}

private GTransaction findPendingTransaction(UUID transactionId, UUID groupId) {
    GTransaction txn = findActiveTransaction(transactionId, groupId);
    if (txn.getStatus() != TransactionStatus.PENDING)
        throw new BusinessException(ErrorCode.TRANSACTION_NOT_PENDING);
    return txn;
}

// danh sách rỗng khi request không có id; ném lỗi nếu có id không hợp lệ hoặc không còn PENDING
private List<GTransaction> findPendingBatch(UUID groupId, GroupTransactionBulkReviewReq req) {
    if (req.transactionIds() == null || req.transactionIds().isEmpty())
        return List.of();

    List<UUID> distinctIds = req.transactionIds().stream().distinct().toList();
    List<GTransaction> txns = transactionRepository
            .findByIdInAndGroupIdAndDeletedAtIsNullAndStatus(distinctIds, groupId, TransactionStatus.PENDING);

    if (txns.size() != distinctIds.size())
        throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    return txns;
}

private long deltaOf(GTransaction txn) {
    return transactionHelper.calculateDelta(txn.getType(), txn.getMoneySource(), txn.getAmount(), false);
}
```

**Hành vi giữ nguyên** (đã đối chiếu từng nhánh):

- Request rỗng: vẫn kiểm tra quyền trước rồi mới trả `0` (list rỗng → vòng lặp không chạy, delta 0 không phát event).
- Thứ tự lỗi: quyền → không tìm thấy/không PENDING → `VALIDATION_ERROR`.
- `bulkConfirm` vẫn chỉ phát một `FundBalanceChangedEvent` cho cả lô.

**Quy trình (TDD theo CLAUDE.md gốc):**

1. Viết test mô tả hành vi *hiện tại* của 4 hàm, chạy trên code cũ → phải xanh (test đặc tả).
2. Refactor.
3. Chạy lại → vẫn xanh.

Test kiểm **trạng thái entity + event đã phát**, *không* `verify(...save(...))`, để không phụ thuộc vào việc có bỏ
`save` hay không. Danh sách test (mô tả tiếng Việt, tên method tiếng Anh):

| Method                                                | Kiểm gì                                                                   |
|-------------------------------------------------------|---------------------------------------------------------------------------|
| `confirm_ByOwner_MarksConfirmedAndPublishesDelta`     | status = CONFIRMED, `reviewedBy`/`reviewedAt` được set, phát đúng 1 event |
| `confirm_ByPlainMember_ThrowsForbidden`               | `FORBIDDEN_TREASURER_REQUIRED`, không phát event                          |
| `confirm_WhenNotPending_ThrowsConflict`               | `TRANSACTION_NOT_PENDING`, không phát event                               |
| `reject_MarksRejectedWithoutFundChange`               | status = REJECTED, **không** phát event                                   |
| `bulkConfirm_EmptyIds_ReturnsZeroWithoutEvent`        | trả 0, không phát event                                                   |
| `bulkConfirm_DuplicateIds_ConfirmsOnceEach`           | id trùng được loại, đếm đúng                                              |
| `bulkConfirm_SomeIdsNotPending_ThrowsValidationError` | `VALIDATION_ERROR`, không đổi trạng thái                                  |
| `bulkConfirm_PublishesSingleEventWithSummedDelta`     | đúng 1 event, delta = tổng                                                |
| `bulkReject_MarksAllRejectedWithoutFundChange`        | tất cả REJECTED, không event                                              |

Chạy test: chỉ `mvn test -Dtest=GTransactionServiceImplTest` (theo `test-rule.md`, không chạy full).

---

## Phần B — Chặn duyệt trùng bằng `@Version`

### 1. File sẽ thay đổi

| File                                                                            | Thay đổi                                                         |
|---------------------------------------------------------------------------------|------------------------------------------------------------------|
| `src/main/resources/db/migration/V13__group_transactions_version.sql` (**mới**) | Thêm cột `version`                                               |
| `.../group/entity/GTransaction.java`                                            | Thêm trường `@Version`                                           |
| `.../common/exception/ErrorCode.java`                                           | Thêm mã lỗi `CONCURRENT_MODIFICATION` (409)                      |
| `.../common/exception/GlobalExceptionHandler.java`                              | Thêm handler cho `ObjectOptimisticLockingFailureException` → 409 |
| `.../group/repository/GroupTransactionRepositoryTest.java`                      | Test khoá lạc quan ở tầng repository                             |
| `docs/design/group/...` hoặc `api` liên quan                                    | Ghi mã lỗi mới nếu tài liệu API nhóm có liệt kê mã lỗi           |

> Trước khi tạo migration phải `ls src/main/resources/db/migration/` kiểm tra lại: hiện cao nhất là `V12`. Nhánh `group`
> đang mở, nếu nhánh khác đã lấy `V13` thì dời số (bài học trùng số đã từng vấp ở dự án).

### 2. Lý do thay đổi

**Lỗi:** `confirm` đọc giao dịch, thấy `PENDING`, rồi mới ghi. Hai request gần như đồng thời (chủ nhóm + thủ quỹ cùng
bấm, bấm đúp, app gửi lại khi mạng chập chờn, hai thiết bị) cùng qua được kiểm tra `PENDING`, **cả hai cùng phát
`FundBalanceChangedEvent` → quỹ bị cộng/trừ hai lần**. Số dư sai nhưng vẫn "trông hợp lý" — đúng loại lỗi khó phát hiện
mà CLAUDE.md cảnh báo.

Hiện trong `group/` không có `@Version`/`@Lock` nào. Listener `FundServiceImpl.onFundBalanceAdjust` chạy **đồng bộ trong
cùng transaction** (`@EventListener`), nên khi request thứ hai bị chặn lúc commit thì cả lần cộng quỹ của nó cũng
**rollback theo** → quỹ chỉ đổi một lần.

**Lợi ích kèm theo:** `update()` (sửa giao dịch) tính delta từ số tiền cũ. Nếu có người duyệt xen giữa lúc đang sửa,
delta tính sai. `@Version` chặn luôn tình huống này.

**Hiệu ứng phụ tốt:** id giao dịch do code tự sinh (`UUID.randomUUID()`), hiện `save()` của bản ghi mới đi qua `merge`
(thêm một câu `SELECT`). Có `@Version` kiểu `Long` (null = mới) thì Spring Data gọi thẳng `persist`, bớt một truy vấn
mỗi lần tạo giao dịch.

**Vì sao chọn `@Version` thay vì `UPDATE ... WHERE status = 'PENDING'`:** thêm một cột + một trường, code nghiệp vụ
không phải đổi. Cách `UPDATE` có điều kiện phải viết lại luồng duyệt và luồng bulk.

### 3. Hướng giải quyết (code xem trước)

**Migration `V13__group_transactions_version.sql`** — `application-dev.yml` đang `ddl-auto: validate` nên bắt buộc có
migration, nếu không app không khởi động:

```sql
ALTER TABLE group_transactions
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN group_transactions.version IS 'Khoá lạc quan: chặn hai request cùng sửa/duyệt một giao dịch';
```

**Entity** (`@Builder` để `version` null cho bản ghi mới, nên JPA coi là mới):

```java
/**
 * Khoá lạc quan: mỗi lần ghi Hibernate tăng số này và chỉ ghi khi số trong DB còn khớp.
 */
@Version
@Column(name = "version", nullable = false)
private Long version;
```

**ErrorCode:**

```java
CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "Dữ liệu vừa được người khác thay đổi, vui lòng tải lại và thử lại."),
```

**GlobalExceptionHandler** — không có handler này thì lỗi rơi vào catch-all `Exception.class` → **500**, app hiện "lỗi
máy chủ":

```java
// hai request cùng ghi một bản ghi: request đến sau bị từ chối, không phải lỗi máy chủ
@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex,
                                                          HttpServletRequest request) {
    // theo đúng khuôn handleBusiness: set ATTR_ERROR_DETAIL, log debug, trả ErrorResponse 409
}
```

**Test (viết trước, phải đỏ đúng lý do):** trong `GroupTransactionRepositoryTest` (Testcontainers, theo mẫu
`FundRepositoryTest`) — test chạy trong một transaction nên mô phỏng hai người bằng cách: load entity →
`UPDATE ... SET version = version + 1` bằng native query (đóng vai request kia đã ghi trước) → đổi trạng thái entity →
`flush` → phải ném `ObjectOptimisticLockingFailureException`. Thêm một test MockMvc/handler kiểm phản hồi là `409` +
code `CONCURRENT_MODIFICATION`.

Chạy: `mvn test -Dtest=GroupTransactionRepositoryTest` và test của handler; ngoài ra chạy lại
`GTransactionServiceImplTest`, `GroupTransactionControllerTest`.

**Rủi ro cần biết:**

- Mọi chỗ sửa `GTransaction` đều bị kiểm version, không chỉ duyệt. Hai request sửa cùng lúc giờ có một bên nhận 409
  (đúng ý, nhưng app cần hiển thị lỗi này).
- Dữ liệu cũ nhận `version = 0` nhờ `DEFAULT 0`, không cần backfill.
- `transactionRepository.save(...)` các chỗ khác (`create`, `update`, `delete`) vẫn chạy bình thường.

---

## Điểm cần bạn quyết (ghi ý kiến vào đây)

| # | Câu hỏi                                                                                                           | Khuyến nghị                                                                                       | Ý kiến của bạn    |
|---|-------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|-------------------|
| 1 | Bỏ `save`/`saveAll` trong 4 hàm duyệt (A5)?                                                                       | Bỏ — entity đã managed, kết quả giống hệt. Nếu thích giữ cho tường minh thì giữ, chỉ mất tính gọn | ok                |
| 2 | Có làm Phần B không, hay để lại ghi vào "hạn chế đã biết"?                                                        | Làm — chỉ 1 migration + vài dòng, còn chặn được cả lỗi sửa-xen-duyệt                              | làm               |
| 3 | Mã lỗi mới `CONCURRENT_MODIFICATION` đặt chung mức 409 toàn hệ thống (ai dùng `@Version` cũng hưởng) — chấp nhận? | Chấp nhận                                                                                         | chấp nhận         |
| 4 | Hai phần commit riêng hay gộp?                                                                                    | Riêng: A trước, B sau                                                                             | không viết commit |

## Ngoài phạm vi (chỉ báo, không làm)

- `@Transactional` đang đặt ở **class-level** (`GTransactionServiceImpl` dòng 52), trái quy tắc 2.5 (chỉ đặt trên public
  method, method đọc dùng `readOnly = true`).
- `create`/`update` cũng có đoạn `calculateDelta(..., false)` giống `deltaOf` mới; có thể dùng lại sau nếu muốn, plan
  này không đụng để giữ thay đổi nhỏ nhất.

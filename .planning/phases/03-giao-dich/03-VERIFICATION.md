---
phase: 03-giao-dich
verified: 2026-08-24T13:30:16Z
status: passed
score: 9/9 must-haves verified
overrides_applied: 0
---

# Phase 3: Giao dịch Verification Report

**Phase Goal:** User ghi nhận, sửa, xoá, nhân bản giao dịch expense/income/transfer với số dư ví luôn
chính xác tuyệt đối kể cả trong các thao tác sửa/xoá phức tạp — đây là module lõi mà mọi phase sau
phụ thuộc.
**Verified:** 2026-08-24
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Toàn hệ thống chỉ còn MỘT đường ghi giao dịch (D-30) | ✓ VERIFIED | `WalletTransferService.java` không còn `INSERT INTO transactions` nào (grep 0 kết quả); `transfer()`/`adjustBalance()` gọi `transactionWriter.write(...)`. `TransactionWriter.write()` là điểm ghi duy nhất, gọi từ `TransactionService.create/duplicate`, `TransactionBulkService.processRow`, `WalletTransferService.transfer/adjustBalance` |
| 2 | Sửa giao dịch đúng 3 bước bất di bất dịch, trong 1 DB transaction, không cộng/trừ chênh lệch (CLAUDE.md §4, D-33) | ✓ VERIFIED | `TransactionService.update()` — Bước 1 `applyEffect(old, reverse=true)` → Bước 2 `save(old)` cùng id → Bước 3 `applyEffect(new, reverse=false)`, toàn bộ trong `@Transactional`. Test `updateChangesAmountAndWallet_bothOldAndNewWalletBalancesAreExactlyCorrect` xác nhận đúng ca kinh điển: đổi expense 100k/ví A → 80k/ví B, ví A hoàn trả đủ 1.000.000, ví B còn 420.000 (KHÔNG phải 480.000 do trừ chênh lệch). Test thứ hai xác nhận ca transfer đổi cả 4 ví A→B thành C→D — khoá đúng `UUID.compareTo()` |
| 3 | Xoá giao dịch gắn `debt_payments` bị chặn 409, không tự ghi `paid_amount`/`status` (D-32) | ✓ VERIFIED | `TransactionService.delete()` kiểm `existsDebtPaymentLink` TRƯỚC khi hoàn tác số dư → 409 `TRANSACTION_LINKED_TO_DEBT`. Grep xác nhận không có dòng nào ghi `debts.paid_amount`/`debt.setPaidAmount` trong `TransactionService.java`. Test `deleteTransactionLinkedToDebtPayment_returns409` xác nhận `is_deleted` vẫn `false` sau lời gọi |
| 4 | Nhân bản giao dịch sang hôm nay, cho phép ghi đè date/amount, source luôn "manual" (TXN-07) | ✓ VERIFIED | `TransactionService.duplicate()` mặc định `LocalDate.now()` (không phải ngày gốc), `source="manual"` cứng, ghi đè `amount`/`date` nếu request có |
| 5 | Bulk tối đa 50 dòng, mỗi dòng transaction DB riêng, dòng lỗi vào `row_errors` không cuốn theo dòng khác (D-34/D-35a) | ✓ VERIFIED | `TransactionBulkService` không có `@Transactional` ở bất kỳ method nào — mỗi `transactionWriter.write()` trong vòng lặp tự mở transaction riêng qua AOP proxy. Test `bulkCreate_withSystemErrorRow_keepsRowsBeforeAndAfterIntact` và `bulkCreate_exceeding50Rows_returns400TooManyRows` xác nhận đúng hành vi |
| 6 | Một `Idempotency-Key` bảo vệ cả lô bulk (D-35) | ✓ VERIFIED | `@Idempotent` gắn ở `TransactionController.createBulk` (tầng Controller, ngoài `processRow`). Test `bulkCreate_replayWithSameIdempotencyKey_doesNotDuplicateRows` xác nhận. Bug `ClassCastException` khi replay trên endpoint trả thẳng `ApiResponse` đã được vá ở `IdempotencyAspect.buildResponseFromCache` (deserialize theo `MethodSignature.getGenericReturnType()` thay vì `Object`) |
| 7 | Lọc/thống kê theo danh mục cha cộng gộp con qua `fn_category_tree`, loại `transfer` khỏi báo cáo (TXN-08, CLAUDE.md §1/§2) | ✓ VERIFIED | `TransactionService.resolveCategoryTree()` là điểm gọi `findCategoryTree` duy nhất (grep xác nhận 1 điểm), dùng chung cho `list()`/`listByDate()`. Câu SQL `summary` hard-code `t.type <> 'transfer'`, `search`/`countSearch` có điều kiện `includeTransfers` |
| 8 | `current_balance`/`projected_balance` đúng nghĩa D-37/D-36 (ngày tương lai, trừ ngược) | ✓ VERIFIED | `WalletResponse`/`WalletDetailResponse` có `@JsonInclude(NON_NULL)` + field `projectedBalance`. `WalletService` gọi `findBalanceAsOf`/`hasFutureTransactions`. Test `walletWithFutureTransaction_returnsBothCurrentAndProjectedBalance` (800.000/1.300.000 đúng) và `walletWithoutFutureTransaction_omitsProjectedBalanceField` (`doesNotExist()`, không phải `isNull()`) |
| 9 | Lỗ hổng quyền ví trong bulk đã vá (T-03-12) | ✓ VERIFIED | `TransactionBulkService.requireWalletAccess()` gọi `WalletRepository.findByIdForUser` (quyền trong SQL) cho cả `walletId` và `destinationWalletId` trước khi ghi. Test `bulkCreate_withWalletOfAnotherUser_rejectsThatRowOnly` xác nhận |

**Score:** 9/9 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `transaction/entity/Transaction.java` | Entity JPA 17 cột theo V2/V8 | ✓ VERIFIED | Tồn tại, map đủ cột kể cả `counts_in_report` (V8) |
| `transaction/service/TransactionWriter.java` | Bean ghi dùng chung (D-31) | ✓ VERIFIED | `@Component`, 1 method `@Transactional`, không self-invocation |
| `wallet/repository/WalletRepository.java` | `findBalanceAsOf`, `hasFutureTransactions` | ✓ VERIFIED | Cả hai method tồn tại, đúng SQL trừ ngược D-36 |
| `transaction/service/TransactionService.java` | create/update/delete/duplicate/list/detail/listByDate | ✓ VERIFIED | Đầy đủ, đúng 3 bước, đúng validateShape dùng chung |
| `transaction/service/TransactionBulkService.java` | Bulk, mỗi dòng transaction riêng | ✓ VERIFIED | Không `@Transactional` cấp method/class, có `requireWalletAccess` |
| `transaction/controller/TransactionController.java` | 7 endpoint (POST/PUT/DELETE/duplicate/bulk/list/by-date/detail) | ✓ VERIFIED | Đủ endpoint, `@Idempotent` đúng chỗ (create/duplicate/bulk) |
| `wallet/dto/WalletResponse.java`, `WalletDetailResponse.java` | current_balance/projected_balance đúng D-37 | ✓ VERIFIED | `@JsonInclude(NON_NULL)`, field `projectedBalance` |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `WalletTransferService` | `TransactionWriter` | `transactionWriter.write(...)` | ✓ WIRED | Cả `transfer()` và `adjustBalance()` gọi, không còn INSERT thô |
| `TransactionService.update/delete` | `WalletRepository.adjustBalance` | `applyEffect()` dùng chung | ✓ WIRED | Đúng bảng chiều tiền expense/income/transfer, cả thuận lẫn đảo |
| `TransactionService.list/listByDate` | `CategoryRepository.findCategoryTree` | `resolveCategoryTree()` | ✓ WIRED | 1 điểm gọi duy nhất, dùng chung 3 query (search/countSearch/summary) |
| `TransactionBulkService` | `TransactionWriter` | vòng lặp không `@Transactional` bao ngoài | ✓ WIRED | Xác nhận qua test hệ thống lỗi giữa lô |
| `TransactionController.createBulk` | `IdempotencyAspect` | `@Idempotent` ở Controller | ✓ WIRED | Test replay xác nhận không ghi trùng |
| `TransactionService.delete` | `TransactionRepository.existsDebtPaymentLink` | kiểm tra trước hoàn tác | ✓ WIRED | Test 409 xác nhận |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Toàn bộ test suite (Phase 1+2+3) | `mvn test` | `Tests run: 100, Failures: 0, Errors: 0, Skipped: 0` — `BUILD SUCCESS` | ✓ PASS |
| Compile sạch | `mvn -q compile` | Thoát mã 0, không lỗi | ✓ PASS |
| Không còn INSERT thô ở WalletTransferService | `grep -c "INSERT INTO transactions" WalletTransferService.java` | 0 | ✓ PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|--------------|--------|----------|
| TXN-01 | 03-03 | GET /transactions lọc đủ tham số + phân trang + summary loại transfer | ✓ SATISFIED | `TransactionRepository.search/countSearch/summary`, test `TransactionCategoryTreeFilterIntegrationTest`/`TransactionListQueryIntegrationTest` |
| TXN-02 | 03-03 | GET /transactions/by-date + GET /transactions/{id} | ✓ SATISFIED | `listByDate()`, `detail()`, test xác nhận nhãn ngày giờ Việt Nam |
| TXN-03 | 03-02 | Tạo giao dịch, validate theo type, ngày tương lai chấp nhận | ✓ SATISFIED | `TransactionService.create()`, `TransactionCrudIntegrationTest` (7 test) |
| TXN-04 | 03-04 | Bulk tối đa 50 dòng, row_errors, 1 idempotency-key/lô | ✓ SATISFIED | `TransactionBulkService`, `TransactionBulkIntegrationTest` (5 test) |
| TXN-05 | 03-02 | Sửa 3 bước bất di bất dịch | ✓ SATISFIED | `TransactionService.update()`, `TransactionUpdateAtomicityIntegrationTest` (ca 100k/80k + 4 ví) |
| TXN-06 | 03-02 | Xoá chặn khi gắn debt_payments, 409 | ✓ SATISFIED | `TransactionService.delete()`, `TransactionDeleteDebtLinkIntegrationTest` |
| TXN-07 | 03-02 | Nhân bản sang hôm nay, ghi đè date/amount | ✓ SATISFIED | `TransactionService.duplicate()` |
| TXN-08 | 03-03 | Cộng gộp danh mục con qua fn_category_tree, dùng 1 điểm gọi chung | ✓ SATISFIED | `resolveCategoryTree()`, grep xác nhận đúng 1 điểm |
| TXN-09 | 03-01 | current_balance/projected_balance đúng D-36/D-37 | ✓ SATISFIED | `WalletFutureBalanceIntegrationTest` (2 test) |

**Lưu ý tài liệu (không phải gap chức năng):** `.planning/REQUIREMENTS.md` còn đánh dấu `[ ]` (chưa
xong) cho TXN-01, TXN-02, TXN-04, TXN-08 dù cả 4 đã hoàn thành và có test xanh (plan 03-03/03-04).
Đây là checkbox chưa đồng bộ, không ảnh hưởng goal achievement — nên cập nhật lại file này ở lần
đóng phase kế tiếp để tránh nhầm lẫn khi tra cứu.

### Anti-Patterns Found

Không tìm thấy TODO/FIXME/PLACEHOLDER/console.log hoặc empty-implementation nào trong
`transaction/` hoặc các file `wallet/` bị sửa ở phase này. Một `return null;` duy nhất
(`TransactionService.resolveCategoryTree`, dòng 490) là hành vi có chủ đích (không filter theo
category khi `categoryId == null`), không phải stub.

### Human Verification Required

Không có mục nào cần kiểm tra thủ công — toàn bộ must-have đều kiểm chứng được qua test tích hợp
Testcontainers PostgreSQL thật (không mock DB), qua HTTP thật (MockMvc), và qua `mvn test` chạy
thực tế xác nhận 100/100 PASS khớp với SUMMARY.

### Gaps Summary

Không có gap chức năng. Cả 4 plan (03-01 đến 03-04) đã thực thi đúng như CONTEXT.md yêu cầu:
gộp đường ghi giao dịch trước tiên (D-30), đúng thứ tự 3 bước sửa giao dịch (D-33), chặn xoá gắn
sổ nợ thay vì tự dọn (D-32), bulk mỗi dòng transaction riêng cộng một idempotency-key chung cho cả
lô (D-34/D-35/D-35a), và sửa đúng lệch nghĩa cột CSDL/trường API cho số dư ví (D-36/D-37). Hai lỗi
tự phát hiện trong lúc thực thi (lỗ hổng quyền ví trong bulk, `ClassCastException` khi replay
idempotency) đều đã được vá và có test chặn hồi quy. `mvn test` xác nhận độc lập 100/100 PASS,
khớp với SUMMARY. Duy nhất một điểm cần dọn dẹp không chức năng: đồng bộ lại checkbox trong
REQUIREMENTS.md cho TXN-01/02/04/08.

---

*Verified: 2026-08-24T13:30:16Z*
*Verifier: Claude (gsd-verifier)*

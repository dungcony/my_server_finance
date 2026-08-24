---
phase: 03-giao-dich
plan: 04
subsystem: transaction
tags: [spring-boot, transaction-propagation, idempotency, security, testcontainers]

# Dependency graph
requires:
  - phase: 03-giao-dich (plan 01)
    provides: "TransactionWriter — điểm ghi duy nhất, tự mang @Transactional"
  - phase: 03-giao-dich (plan 02)
    provides: "TransactionService.validateShape() package-private, CreateTransactionRequest"
  - phase: 03-giao-dich (plan 03)
    provides: "TransactionListItemResponse, TransactionService.buildListItemResponse()"
provides:
  - "POST /transactions/bulk — tối đa 50 dòng, mỗi dòng một DB transaction riêng, row_errors từng dòng"
  - "TransactionBulkService — không @Transactional ở bất kỳ method nào (mấu chốt D-34/D-35a)"
  - "BulkCreateTransactionRequest, BulkCreateTransactionResponse (RowError, NewBalanceItem)"
  - "IdempotencyAspect.buildResponseFromCache đã sửa — deserialize đúng kiểu trả về của method"
affects: [05-tro-ly-ai (duyệt AI draft hàng loạt sẽ gọi endpoint này), mọi endpoint @Idempotent trả thẳng ApiResponse]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Đạt 'mỗi dòng một DB transaction riêng' bằng cách KHÔNG đặt @Transactional lên method vòng lặp — mỗi transactionWriter.write() là lời gọi cross-bean qua AOP proxy nên tự mở transaction của chính nó. Không cần propagation = REQUIRES_NEW tường minh"
    - "@Idempotent bọc ở tầng Controller (không phải Service) nên IdempotencyAspect không mở transaction bao trùm cả lô — đây là cách hoá giải mâu thuẫn D-34 vs D-35"
    - "catch BusinessException -> row_errors; catch DataAccessException -> row_errors với code INTERNAL_ERROR. Không catch Exception chung, để lỗi ngoài dự kiến vẫn nổi lên GlobalExceptionHandler"
    - "Kiểm quyền ví bằng WalletRepository.findByIdForUser — điều kiện quyền nằm NGAY TRONG SQL (CLAUDE.md §7), không lọc ở tầng Java, không có quyền trả 404 chứ không phải 403"
    - "Bulk KHÔNG khoá bi quan FOR UPDATE như TransactionService.lockWalletsInOrder: mỗi dòng là transaction riêng nên không có nguy cơ deadlock giữa các dòng cùng lô, và adjustBalance vốn là UPDATE nguyên tử (current_balance = current_balance + ?) nên không cần đọc-rồi-ghi"

key-files:
  created:
    - src/test/java/com/datn/financeapp/transaction/TransactionBulkIntegrationTest.java
  modified:
    - src/main/java/com/datn/financeapp/transaction/service/TransactionBulkService.java
    - src/main/java/com/datn/financeapp/common/idempotency/IdempotencyAspect.java
  created_in_prior_commit:
    - src/main/java/com/datn/financeapp/transaction/dto/BulkCreateTransactionRequest.java
    - src/main/java/com/datn/financeapp/transaction/dto/BulkCreateTransactionResponse.java
    - src/main/java/com/datn/financeapp/transaction/service/TransactionBulkService.java

key-decisions:
  - "Vá lỗ hổng quyền ví (T-03-12) — bản dựng đầu của processRow chỉ kiểm quyền danh mục, bỏ hẳn kiểm quyền ví, cho phép người dùng ghi giao dịch vào ví của người khác. TransactionService.create() vốn kiểm gián tiếp qua lockWalletsInOrder(), bulk không dùng method đó nên mất luôn bước kiểm"
  - "Sửa IdempotencyAspect thay vì lách ở tầng bulk — bug nằm ở hạ tầng dùng chung, ảnh hưởng mọi endpoint @Idempotent trả thẳng ApiResponse, không riêng bulk"
  - "Không mô phỏng lỗi hạ tầng thật (mất kết nối CSDL) trong test — không làm được qua Testcontainers mà không phá container. Dùng ví không tồn tại: vẫn chứng minh đúng tính chất 'dòng hỏng giữa lô không cuốn theo dòng trước/sau', và ghi rõ giới hạn này trong Javadoc test thay vì mô tả sai sự thật"

requirements-completed: [TXN-04]

# Metrics
duration: 40min
completed: 2026-08-24
---

# Phase 3 Plan 4: Bulk tạo giao dịch Summary

**`POST /transactions/bulk` — tối đa 50 dòng, mỗi dòng một DB transaction riêng nên dòng hỏng giữa lô không cuốn theo dòng khác, một Idempotency-Key bảo vệ cả lô. Kèm hai bản vá: lỗ hổng quyền ví trong bulk, và lỗi cache idempotency ở hạ tầng dùng chung.**

## Performance

- **Duration:** ~40 phút
- **Tasks:** 1
- **Files modified:** 4 (1 tạo mới, 3 sửa)

## Accomplishments

- `POST /transactions/bulk` nhận tối đa 50 dòng; vượt hạn mức trả `TOO_MANY_ROWS` 400 ngay đầu, không xử lý dòng nào (T-03-11)
- **D-34 — mỗi dòng một DB transaction riêng:** `TransactionBulkService` không mang `@Transactional` ở bất kỳ method nào, nên mỗi `transactionWriter.write()` trong vòng lặp là lời gọi cross-bean đi qua AOP proxy thật và tự mở transaction của chính nó. Dòng lỗi chỉ vào `row_errors`, không chặn dòng khác
- **D-35 — một Idempotency-Key cho cả lô:** `@Idempotent` gắn ở tầng Controller; gửi lại cùng key trả nguyên kết quả lần đầu, không ghi thêm bản ghi nào
- **Vá lỗ hổng bảo mật T-03-12:** bản dựng đầu của `processRow` kiểm quyền danh mục nhưng **bỏ hẳn kiểm quyền ví** — người dùng có thể ghi giao dịch vào ví của người khác qua đường bulk. `TransactionService.create()` kiểm gián tiếp qua `lockWalletsInOrder()`, bulk không dùng method đó nên mất luôn bước kiểm. Thêm `requireWalletAccess()` dùng `WalletRepository.findByIdForUser` (quyền kiểm trong SQL, trả 404)
- **Vá bug hạ tầng `IdempotencyAspect`:** `buildResponseFromCache` deserialize cache thành `Object` (ra `LinkedHashMap`), CGLIB proxy của controller ép về kiểu khai báo rồi ném `ClassCastException` ngay tại dispatcher. Nay deserialize đúng kiểu lấy từ `MethodSignature.getGenericReturnType()`. **Lỗi này ảnh hưởng mọi endpoint `@Idempotent` trả thẳng `ApiResponse`**, chỉ lộ ra khi có test replay thật
- 5 test tích hợp mới; **100/100 test toàn dự án PASS**

## Task Commits

1. **Task 1** — `37ba200` (feat): `requireWalletAccess`, sửa `IdempotencyAspect`, `TransactionBulkIntegrationTest`. Phần khung `TransactionBulkService` + 2 DTO đã nằm sẵn trong commit `01e8222` của plan 03-03 (agent 03-03 làm lấn sang).

## Files Created/Modified

- `transaction/service/TransactionBulkService.java` - +`requireWalletAccess()`, +inject `WalletRepository`
- `common/idempotency/IdempotencyAspect.java` - `buildResponseFromCache` deserialize đúng kiểu trả về; bỏ `returnsResponseEntity()` không còn dùng
- `transaction/TransactionBulkIntegrationTest.java` - 5 test: dòng lỗi dữ liệu, dòng hỏng giữa lô, quá 50 dòng, replay idempotency, ví của người khác

## Decisions Made

- Vá `IdempotencyAspect` thay vì lách riêng cho bulk — lỗi nằm ở hạ tầng dùng chung.
- Bulk không khoá bi quan `FOR UPDATE`: mỗi dòng là transaction riêng nên không có deadlock giữa các dòng cùng lô, và `adjustBalance` vốn nguyên tử.
- Test D-35a dùng ví không tồn tại (bị chặn ở tầng service) thay vì lỗi hạ tầng thật; ghi rõ giới hạn này trong Javadoc thay vì mô tả sai bản chất.

## Deviations from Plan

- Plan viết một Task; thực tế phần khung service + DTO đã được agent plan 03-03 tạo sẵn và commit trong `01e8222`. Plan 03-04 do đó tập trung vào rà soát, vá hai lỗi phát hiện được, và viết test.
- Plan gợi ý mô phỏng lỗi hệ thống bằng FK constraint của Postgres. Sau khi thêm `requireWalletAccess`, ví không tồn tại bị chặn ở tầng service trước khi chạm CSDL nên không còn là lỗi FK — tính chất cần chứng minh (dòng hỏng không cuốn theo dòng khác) vẫn nguyên vẹn.

## Issues Encountered

- **Lỗ hổng quyền ví trong bulk** (đã vá, xem Accomplishments) — nghiêm trọng nhất trong phase này vì vi phạm CLAUDE.md §7 "riêng tư mặc định".
- **`ClassCastException` khi replay idempotency** (đã vá) — nằm im từ Phase 1, không test nào bắt được vì chưa có test replay thật trên endpoint trả thẳng `ApiResponse`.

## User Setup Required

None.

## Next Phase Readiness

- Phase 5 (trợ lý AI) duyệt nhiều `ai_drafts` một lượt gọi thẳng endpoint này.
- Bản vá `IdempotencyAspect` có lợi cho mọi endpoint `@Idempotent` hiện có và sau này.
- Không có blocker.

---
*Phase: 03-giao-dich*
*Completed: 2026-08-24*

## Self-Check: PASSED

- FOUND: src/main/java/com/datn/financeapp/transaction/service/TransactionBulkService.java
- FOUND: src/test/java/com/datn/financeapp/transaction/TransactionBulkIntegrationTest.java
- COMMIT: 37ba200 feat(03-04): bulk tạo giao dịch, vá lỗ hổng quyền ví và lỗi cache idempotency
- TEST: 100/100 PASS (mvn test, BUILD SUCCESS)
- CRITERIA: grep "@Transactional" TransactionBulkService.java → chỉ có trong Javadoc, không có annotation thật
- CRITERIA: grep "MAX_ROWS = 50" TransactionBulkService.java → có kết quả (dòng 38)

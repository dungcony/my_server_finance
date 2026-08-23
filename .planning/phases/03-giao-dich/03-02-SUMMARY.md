---
phase: 03-giao-dich
plan: 02
subsystem: transaction
tags: [spring-boot, jpa, postgresql, testcontainers, wallet-balance, atomicity]

# Dependency graph
requires:
  - phase: 03-giao-dich (plan 01)
    provides: "transaction/entity/Transaction.java, transaction/repository/TransactionRepository.java, transaction/service/TransactionWriter.java + TransactionWriteCommand — điểm ghi giao dịch duy nhất"
provides:
  - "transaction/service/TransactionService.java — create/update/delete/duplicate, trình tự 3 bước bất di bất dịch khi sửa, khoá tối đa 4 ví theo UUID.compareTo(), chặn xoá gắn debt_payments"
  - "transaction/controller/TransactionController.java — POST/PUT/DELETE/POST duplicate /transactions"
  - "transaction/dto/* — CreateTransactionRequest, UpdateTransactionRequest, DuplicateTransactionRequest, TransactionResponse, CreateTransactionResponse, DeleteTransactionResponse"
  - "TransactionRepository.findByIdAndUserId — không lọc is_deleted, dùng cho DELETE idempotent (CORE-06)"
  - "TransactionService.validateShape() — package-private, dùng lại cho TransactionBulkService (plan 03-04)"
  - "api/04-GIAO-DICH.md bổ sung mã lỗi DESTINATION_WALLET_NOT_ALLOWED (repo gốc DATN)"
affects: [03-giao-dich (plan 03, 04), 04-ngan-sach-bao-cao-vv (Phase 4 debt/goal sẽ gọi lại TransactionService/TransactionWriter)]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "update()/delete() gọi trực tiếp TransactionRepository.save() + WalletRepository.adjustBalance() trong CÙNG @Transactional — KHÔNG qua TransactionWriter (writer chỉ INSERT bản ghi mới, không phù hợp cho sửa tại chỗ)"
    - "Trình tự 3 bước sửa giao dịch: hoàn tác ảnh hưởng CŨ (applyEffect reverse=true) → ghi giá trị MỚI (transactionRepository.save trên cùng entity, cùng id) → áp dụng ảnh hưởng MỚI (applyEffect reverse=false) — dùng chung 1 method applyEffect() cho cả bước 1 và bước 3, và cho cả delete()"
    - "lockWalletsInOrder() gom Set<UUID> loại trùng trước khi khoá — sửa transfer A→B thành A→C chỉ khoá 3 ví, không phải 4; sắp UUID.compareTo() tăng dần chống deadlock, mở rộng nguyên thuật toán 2-ví của WalletTransferService"
    - "validateShape() package-private (không private, không public) — cho phép TransactionBulkService (plan 03-04, cùng package) gọi lại validate từng dòng bulk mà không lặp code, không mở rộng phạm vi truy cập quá cần thiết"
    - "Validate ràng buộc theo type (category/destination bắt buộc-rỗng) chạy Ở TẦNG SERVICE TRƯỚC KHI chạm CSDL — không dựa vào ck_txn_shape/trigger DB để báo lỗi nghiệp vụ, constraint DB chỉ là lưới an toàn cuối"
    - "DELETE idempotent (CORE-06): TransactionRepository.findByIdAndUserId (không lọc is_deleted) phân biệt 404 (không tồn tại/của người khác) với 200 no-op (đã xoá mềm từ trước)"

key-files:
  created:
    - src/main/java/com/datn/financeapp/transaction/dto/CreateTransactionRequest.java
    - src/main/java/com/datn/financeapp/transaction/dto/UpdateTransactionRequest.java
    - src/main/java/com/datn/financeapp/transaction/dto/DuplicateTransactionRequest.java
    - src/main/java/com/datn/financeapp/transaction/dto/TransactionResponse.java
    - src/main/java/com/datn/financeapp/transaction/dto/CreateTransactionResponse.java
    - src/main/java/com/datn/financeapp/transaction/dto/DeleteTransactionResponse.java
    - src/main/java/com/datn/financeapp/transaction/service/TransactionService.java
    - src/main/java/com/datn/financeapp/transaction/controller/TransactionController.java
    - src/test/java/com/datn/financeapp/transaction/TransactionCrudIntegrationTest.java
    - src/test/java/com/datn/financeapp/transaction/TransactionUpdateAtomicityIntegrationTest.java
    - src/test/java/com/datn/financeapp/transaction/TransactionDeleteDebtLinkIntegrationTest.java
  modified:
    - src/main/java/com/datn/financeapp/transaction/repository/TransactionRepository.java
    - src/test/java/com/datn/financeapp/common/exception/GlobalExceptionHandlerTest.java
    - ../../api/04-GIAO-DICH.md

key-decisions:
  - "Thêm mã lỗi mới DESTINATION_WALLET_NOT_ALLOWED (400) — đối xứng với CATEGORY_NOT_ALLOWED đã có, vá lỗ hổng tài liệu chưa từng mô tả ca chi/thu kèm ví đích dù ck_txn_shape (V2) đã chặn cứng ở CSDL"
  - "TransactionService.applyEffect() dùng chung cho cả bước 1 (hoàn tác, reverse=true) và bước 3 (áp dụng mới, reverse=false) của update(), và cho bước hoàn tác của delete() — tránh viết lặp logic đảo dấu theo type 3 lần"
  - "validateShape() để package-private thay vì private — chuẩn bị điểm nối cho TransactionBulkService (plan 03-04) gọi lại đúng logic validate mà không cần public hoá không cần thiết"

requirements-completed: [TXN-03, TXN-05, TXN-06, TXN-07]

# Metrics
duration: 45min
completed: 2026-08-23
---

# Phase 3 Plan 2: CRUD giao dịch đơn lẻ — sửa đúng 3 bước, chặn xoá gắn nợ Summary

**TransactionService.create/update/delete/duplicate — trình tự 3 bước hoàn tác/ghi mới/áp dụng mới bất di bất dịch khi sửa giao dịch (kiểm chứng bằng ca kinh điển 100k/80k đổi cả amount lẫn ví), khoá tối đa 4 ví theo UUID.compareTo(), chặn xoá giao dịch gắn debt_payments (409), thêm mã lỗi DESTINATION_WALLET_NOT_ALLOWED vào tài liệu API.**

## Performance

- **Duration:** ~45 min
- **Tasks:** 3
- **Files modified:** 14 (11 tạo mới, 3 sửa)

## Accomplishments

- `TransactionService.create()`: validate đầy đủ theo `type` ở tầng service trước khi chạm CSDL (category bắt buộc/rỗng, destination bắt buộc/rỗng, khớp loại danh mục), ghi qua `TransactionWriter` (D-31, điểm ghi duy nhất), chấp nhận ngày tương lai không giới hạn (D-36 — không có `if (date.isAfter(...))` nào)
- `TransactionService.update()`: trình tự 3 bước bất di bất dịch (CLAUDE.md §4) trong MỘT `@Transactional` — hoàn tác ảnh hưởng CŨ lên (các) ví CŨ → ghi giá trị MỚI vào cùng bản ghi (cùng `id`, không tạo entity mới) → áp dụng ảnh hưởng MỚI lên (các) ví MỚI. Khoá tối đa 4 ví (D-33) theo `UUID.compareTo()` tăng dần, gom `Set<UUID>` loại trùng trước khi khoá
- `TransactionService.delete()`: kiểm tra `existsDebtPaymentLink` TRƯỚC khi hoàn tác số dư — có liên kết thì 409 `TRANSACTION_LINKED_TO_DEBT`, không đụng gì tới `debts.paid_amount`/`debts.status` (cột do trigger `trg_debt_payments_sync` sở hữu). Xoá mềm idempotent theo CORE-06 (gọi lại vẫn 200, không lỗi)
- `TransactionService.duplicate()`: nhân bản sang ngày hôm nay (mặc định, không phải ngày gốc), `source` luôn `"manual"`, cho phép ghi đè `date`/`amount`
- `TransactionController`: 4 endpoint `POST/PUT/DELETE /transactions`, `POST /transactions/{id}/duplicate` — POST tạo mới và duplicate gắn `@Idempotent`, PUT/DELETE không gắn (cùng lý do `WalletController`)
- Mã lỗi mới `DESTINATION_WALLET_NOT_ALLOWED` (400) — Task 1 phát hiện tài liệu thiếu mã cho ca "chi/thu có ví đích" dù `ck_txn_shape` (V2) đã chặn cứng; vá `api/04-GIAO-DICH.md` (repo gốc DATN) ở cả bảng mã lỗi riêng và bảng tóm tắt cuối file
- 3 bộ test tích hợp mới qua HTTP thật (Testcontainers PostgreSQL): `TransactionCrudIntegrationTest` (7 test — create theo 3 type, validate đầy đủ, ngày tương lai được chấp nhận), `TransactionUpdateAtomicityIntegrationTest` (2 test — ca kinh điển 100k/80k đổi ví PASS: ví cũ hoàn trả đủ 100.000, ví mới trừ đúng 80.000 chứ không phải 60.000/20.000 chênh lệch; và ca transfer đổi cả 4 ví A→B thành C→D), `TransactionDeleteDebtLinkIntegrationTest` (1 test — xoá giao dịch gắn `debt_payments` trả 409, `is_deleted` vẫn `false` sau lời gọi)
- Toàn bộ 89 test của project (79 Phase 1+2+03-01 cũ + 10 test mới) PASS — không có regression

## Task Commits

Each task was committed atomically:

1. **Task 1: DTO giao dịch + TransactionService.create() (TXN-03)** - `3b2780c` (feat) — commit này cũng gồm luôn phần khung `update()`/`delete()`/`duplicate()` (viết cùng lúc để đảm bảo tính nhất quán logic 3 bước, do cùng một class `TransactionService`)
2. **Task 2: test atomicity sửa giao dịch + chặn xoá gắn nợ (TXN-05, TXN-06, TXN-07)** - `23f4f08` (test)
3. **Task 3: bổ sung mã lỗi DESTINATION_WALLET_NOT_ALLOWED** - `d8d9dcf` (docs, commit riêng ở repo gốc `D:\PTIT\DATN`)

**Plan metadata:** (commit tiếp theo sau summary này)

## Files Created/Modified

- `src/main/java/com/datn/financeapp/transaction/dto/CreateTransactionRequest.java` - Body POST /transactions
- `src/main/java/com/datn/financeapp/transaction/dto/UpdateTransactionRequest.java` - Body PUT /transactions/{id}
- `src/main/java/com/datn/financeapp/transaction/dto/DuplicateTransactionRequest.java` - Body POST /transactions/{id}/duplicate
- `src/main/java/com/datn/financeapp/transaction/dto/TransactionResponse.java` - Phần tử giao dịch (dùng lại ở plan 03-03)
- `src/main/java/com/datn/financeapp/transaction/dto/CreateTransactionResponse.java` - Phản hồi 201 create/duplicate
- `src/main/java/com/datn/financeapp/transaction/dto/DeleteTransactionResponse.java` - Phản hồi 200 delete
- `src/main/java/com/datn/financeapp/transaction/service/TransactionService.java` - create/update/delete/duplicate + validateShape/lockWalletsInOrder/applyEffect
- `src/main/java/com/datn/financeapp/transaction/controller/TransactionController.java` - 4 endpoint CRUD
- `src/main/java/com/datn/financeapp/transaction/repository/TransactionRepository.java` - +findByIdAndUserId (không lọc is_deleted)
- `src/test/java/com/datn/financeapp/common/exception/GlobalExceptionHandlerTest.java` - +TransactionController vào excludeFilters
- `src/test/java/com/datn/financeapp/transaction/TransactionCrudIntegrationTest.java` - 7 test create
- `src/test/java/com/datn/financeapp/transaction/TransactionUpdateAtomicityIntegrationTest.java` - 2 test 3-bước
- `src/test/java/com/datn/financeapp/transaction/TransactionDeleteDebtLinkIntegrationTest.java` - 1 test chặn xoá gắn nợ
- `../../api/04-GIAO-DICH.md` (repo gốc DATN) - +DESTINATION_WALLET_NOT_ALLOWED

## Decisions Made

- `DESTINATION_WALLET_NOT_ALLOWED` là mã lỗi mới, đối xứng `CATEGORY_NOT_ALLOWED` — không dùng `VALIDATION_ERROR` chung của CORE-01 vì đây là ràng buộc nghiệp vụ riêng của giao dịch, cùng nhóm 5 mã 400 khác đã có sẵn trong bảng.
- `update()`/`delete()` gọi trực tiếp `TransactionRepository.save()` + `WalletRepository.adjustBalance()` thay vì qua `TransactionWriter` — writer chỉ INSERT bản ghi mới, không phù hợp sửa tại chỗ; không rủi ro self-invocation vì `TransactionService` là bean khác `TransactionWriter`/`WalletTransferService`.
- `applyEffect()` dùng chung một method cho cả hoàn tác (bước 1, `reverse=true`) và áp dụng mới (bước 3, `reverse=false`), tránh lặp logic đảo dấu theo `type` ba lần (update bước 1, update bước 3, delete).
- `validateShape()` để package-private — chuẩn bị sẵn điểm nối cho `TransactionBulkService` (plan 03-04) gọi lại đúng logic validate từng dòng bulk.

## Deviations from Plan

None - plan thực thi đúng như đã viết. Logic `update()`/`delete()`/`duplicate()` được viết cùng lúc với `create()` ở Task 1 (thay vì tách riêng ở Task 2) vì cả 4 method thuộc cùng một class `TransactionService` và chia sẻ các private method `validateShape`/`lockWalletsInOrder`/`applyEffect` — tách code ra làm hai commit sẽ để lại các method chưa dùng tới ở Task 1. Test tương ứng (`TransactionUpdateAtomicityIntegrationTest`, `TransactionDeleteDebtLinkIntegrationTest`) được viết và commit ở Task 2 đúng theo `<behavior>` của plan.

## Issues Encountered

Không có vấn đề nào. Compile sạch ngay từ đầu, tất cả 10 test mới PASS ngay lần chạy đầu tiên sau khi sửa 2 điểm nhỏ trong lúc viết test:
- `debts` có 2 cột `NOT NULL` không được đặc tả trong `<behavior>` của plan (`wallet_id`, `origin_transaction_id`) — bổ sung khi viết `INSERT` thủ công trong `TransactionDeleteDebtLinkIntegrationTest`, đối chiếu đúng `V4__so_no_muc_tieu.sql`.
- Ví mặc định "Tiền mặt" do `AuthService.register()` tạo có `initial_balance = 0`, không phải 500.000 như giả định ban đầu khi viết test — sửa `TransactionCrudIntegrationTest` để tự tạo ví có số dư biết trước thay vì dựa vào ví mặc định.

## User Setup Required

None - không có cấu hình dịch vụ ngoài nào cần thiết lập thủ công.

## Next Phase Readiness

- `TransactionService` sẵn sàng làm nền cho plan 03-03 (GET list/detail — tái dùng `TransactionResponse`) và plan 03-04 (`TransactionBulkService` gọi lại `validateShape()`).
- Phase 4 (debt/goal) sẽ gọi lại `TransactionService`/`TransactionWriter` khi cần tạo/xoá giao dịch phát sinh từ sổ nợ — không cần viết lại logic 3 bước hay khoá ví.
- Không có blocker.

---
*Phase: 03-giao-dich*
*Completed: 2026-08-23*

## Self-Check: PASSED

- FOUND: src/main/java/com/datn/financeapp/transaction/dto/CreateTransactionRequest.java
- FOUND: src/main/java/com/datn/financeapp/transaction/dto/UpdateTransactionRequest.java
- FOUND: src/main/java/com/datn/financeapp/transaction/dto/DuplicateTransactionRequest.java
- FOUND: src/main/java/com/datn/financeapp/transaction/dto/TransactionResponse.java
- FOUND: src/main/java/com/datn/financeapp/transaction/dto/CreateTransactionResponse.java
- FOUND: src/main/java/com/datn/financeapp/transaction/dto/DeleteTransactionResponse.java
- FOUND: src/main/java/com/datn/financeapp/transaction/service/TransactionService.java
- FOUND: src/main/java/com/datn/financeapp/transaction/controller/TransactionController.java
- FOUND: src/test/java/com/datn/financeapp/transaction/TransactionCrudIntegrationTest.java
- FOUND: src/test/java/com/datn/financeapp/transaction/TransactionUpdateAtomicityIntegrationTest.java
- FOUND: src/test/java/com/datn/financeapp/transaction/TransactionDeleteDebtLinkIntegrationTest.java
- FOUND: commit 3b2780c
- FOUND: commit 23f4f08
- FOUND: commit d8d9dcf (repo gốc DATN)

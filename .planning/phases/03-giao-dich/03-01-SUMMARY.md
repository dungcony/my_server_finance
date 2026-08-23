---
phase: 03-giao-dich
plan: 01
subsystem: transaction
tags: [spring-boot, jpa, postgresql, testcontainers, wallet-balance]

# Dependency graph
requires:
  - phase: 02-vi-danh-muc (plan 02-02, 02-04)
    provides: "wallet/entity/Wallet.java, wallet/repository/WalletRepository.java (CRUD + D-27 + adjustBalance atomic), wallet/service/WalletTransferService.java (transfer/adjustBalance/reconcile)"
provides:
  - "transaction/entity/Transaction.java — entity JPA đầy đủ 17 cột theo V2/V8"
  - "transaction/repository/TransactionRepository.java — quyền suy từ user_id, existsDebtPaymentLink cho D-32"
  - "transaction/service/TransactionWriter.java + TransactionWriteCommand — bean ghi giao dịch dùng chung duy nhất cho toàn hệ thống (D-31)"
  - "wallet/repository/WalletRepository.java — findBalanceAsOf (D-36 trừ ngược), hasFutureTransactions (D-37), findCurrentBalanceNative (đọc số dư bỏ qua Hibernate cache)"
  - "WalletResponse/WalletDetailResponse trả đúng current_balance (tiền thật đến hết hôm nay) và projected_balance (chỉ khi có giao dịch tương lai)"
affects: [03-giao-dich (plan 02, 03, 04), 04-ngan-sach-bao-cao-vv]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "TransactionWriter là bean hạ tầng riêng (@Component, không @Service) — một method @Transactional duy nhất, không self-invocation, dùng chung cho TransactionService/WalletTransferService/Phase 4"
    - "D-36: suy số dư ví tại một mốc bằng cách trừ ngược phần giao dịch nằm sau mốc, không cộng xuôi từ initial_balance — giữ được hai nguồn độc lập để đối chiếu"
    - "D-37: cột DB current_balance != trường API current_balance — trường API luôn đã trừ ngược, projected_balance = cột DB thô, chỉ trả khi có giao dịch tương lai, @JsonInclude(NON_NULL) để bỏ hẳn trường khi null"
    - "Đọc lại số dư sau adjustBalance() (@Modifying bulk UPDATE) phải dùng native query độc lập persistence context nếu caller đã load entity đó qua JPQL trong cùng transaction trước đó — nếu không sẽ đọc lại instance cache cũ (Hibernate identity map), không phải giá trị mới nhất trong DB"

key-files:
  created:
    - src/main/java/com/datn/financeapp/transaction/entity/Transaction.java
    - src/main/java/com/datn/financeapp/transaction/repository/TransactionRepository.java
    - src/main/java/com/datn/financeapp/transaction/service/TransactionWriter.java
    - src/main/java/com/datn/financeapp/transaction/service/TransactionWriteCommand.java
    - src/test/java/com/datn/financeapp/wallet/WalletFutureBalanceIntegrationTest.java
  modified:
    - src/main/java/com/datn/financeapp/wallet/repository/WalletRepository.java
    - src/main/java/com/datn/financeapp/wallet/service/WalletTransferService.java
    - src/main/java/com/datn/financeapp/wallet/service/WalletService.java
    - src/main/java/com/datn/financeapp/wallet/dto/WalletResponse.java
    - src/main/java/com/datn/financeapp/wallet/dto/WalletDetailResponse.java

key-decisions:
  - "TransactionWriter đọc số dư mới bằng WalletRepository.findCurrentBalanceNative (native SQL, bỏ qua entity manager) thay vì findByIdForUpdate (JPQL) — phát hiện bug tự động khi chạy full test suite: WalletTransferService đã load Wallet qua JPQL trước khi gọi TransactionWriter trong cùng transaction, nên gọi lại findByIdForUpdate trả về đúng instance đã cache (Hibernate identity map) với số dư CŨ, không phải giá trị sau adjustBalance()"
  - "TransactionWriter là @Component thay vì @Service — bean hạ tầng thuần tuý (D-31), không mang logic nghiệp vụ"
  - "Logic tra danh mục hệ thống 'Cập nhật số dư' (V8) giữ nguyên trong WalletTransferService.adjustBalance(), không chuyển vào TransactionWriter — đây là nghiệp vụ đặc thù của kiểm kê ví, TransactionWriter không biết gì về business rule gọi nó"
  - "@JsonInclude(NON_NULL) đặt trực tiếp trên WalletResponse/WalletDetailResponse thay vì cấu hình Jackson toàn cục — dự án chưa có cấu hình NON_NULL nào trước đó, chỉ 2 DTO này cần bỏ trường null có chủ đích (D-37), các DTO khác vẫn trả null bình thường"

requirements-completed: [TXN-09]

# Metrics
duration: 14min
completed: 2026-08-23
---

# Phase 3 Plan 1: Gộp đường ghi giao dịch + sửa hai trường số dư ví Summary

**Tạo entity Transaction + TransactionWriter làm đường ghi giao dịch DUY NHẤT cho toàn hệ thống, gỡ bỏ hai đoạn INSERT SQL thô còn sót lại trong WalletTransferService (di sản Phase 2), đồng thời sửa WalletResponse/WalletDetailResponse trả đúng nghĩa current_balance (tiền thật đến hết hôm nay, đã trừ ngược giao dịch tương lai) và projected_balance (chỉ xuất hiện khi ví có giao dịch tương lai).**

## Performance

- **Duration:** ~14 min
- **Tasks:** 2
- **Files modified:** 10 (5 tạo mới, 5 sửa)

## Accomplishments

- `Transaction` entity ánh xạ đầy đủ 17 cột bảng `transactions` theo `V2__giao_dich.sql` + `V8__dieu_chinh_so_du.sql`, `id` sinh ở tầng service, Javadoc ghi rõ ranh giới `updated_at` do trigger `trg_transactions_validate` sở hữu
- `TransactionRepository`: `findByIdAndUserIdAndIsDeletedFalse` (quyền D-27 suy thẳng từ `user_id`, không có nhánh nhóm vì bảng `transactions` không có cột `group_id`), `existsDebtPaymentLink` viết sẵn cho D-32 (plan sau)
- `TransactionWriter` (`@Component`, D-31): một method `@Transactional` duy nhất ghi bản ghi giao dịch + cập nhật số dư (các) ví theo đúng chiều tiền `expense`/`income`/`transfer`, không self-invocation, không biết gì về nghiệp vụ gọi nó
- `WalletRepository` thêm 3 method: `findBalanceAsOf` (D-36, trừ ngược theo mốc thời gian), `hasFutureTransactions` (D-37), `findCurrentBalanceNative` (đọc số dư bỏ qua Hibernate first-level cache — thêm khi tự phát hiện bug, xem Deviations)
- `WalletTransferService.transfer()` và `.adjustBalance()` không còn tự `jdbcTemplate.update("INSERT INTO transactions...")` — cả hai gọi `transactionWriter.write(...)` và dùng `WriteResult` trả về thay vì tự cộng trừ tay
- `WalletResponse`/`WalletDetailResponse` thêm field `projectedBalance`, gắn `@JsonInclude(NON_NULL)`; `WalletService.toResponse()`/`.detail()` gọi `findBalanceAsOf`/`hasFutureTransactions` thay vì map thẳng cột `wallet.getCurrentBalance()`
- `WalletFutureBalanceIntegrationTest` mới: 2 test qua HTTP thật (Testcontainers PostgreSQL) — ví có giao dịch tương lai trả đúng cả `current_balance` và `projected_balance`; ví không có giao dịch tương lai thì JSON response KHÔNG chứa field `projected_balance` (`jsonPath(...).doesNotExist()`)
- Toàn bộ 79 test của project (77 Phase 1+2 cũ + 2 test mới) PASS sau khi hoàn thành plan — không có regression

## Task Commits

Each task was committed atomically:

1. **Task 1: Transaction entity + TransactionRepository + TransactionWriter + WalletRepository (D-36/D-37 method)** - `726fb5d` (feat)
2. **Task 2: Gộp WalletTransferService qua TransactionWriter + sửa 2 trường số dư ví** - `24ae6d1` (feat)

**Plan metadata:** (commit tiếp theo sau summary này)

## Files Created/Modified

- `src/main/java/com/datn/financeapp/transaction/entity/Transaction.java` - Entity JPA 17 cột
- `src/main/java/com/datn/financeapp/transaction/repository/TransactionRepository.java` - Quyền D-27 + existsDebtPaymentLink
- `src/main/java/com/datn/financeapp/transaction/service/TransactionWriter.java` - Bean ghi giao dịch dùng chung
- `src/main/java/com/datn/financeapp/transaction/service/TransactionWriteCommand.java` - Tham số nguyên thuỷ cho write()
- `src/main/java/com/datn/financeapp/wallet/repository/WalletRepository.java` - +findBalanceAsOf, +hasFutureTransactions, +findCurrentBalanceNative
- `src/main/java/com/datn/financeapp/wallet/service/WalletTransferService.java` - transfer/adjustBalance gọi TransactionWriter
- `src/main/java/com/datn/financeapp/wallet/service/WalletService.java` - toResponse/detail dùng trừ ngược + hasFutureTransactions
- `src/main/java/com/datn/financeapp/wallet/dto/WalletResponse.java` - +projectedBalance, @JsonInclude(NON_NULL)
- `src/main/java/com/datn/financeapp/wallet/dto/WalletDetailResponse.java` - +projectedBalance, @JsonInclude(NON_NULL)
- `src/test/java/com/datn/financeapp/wallet/WalletFutureBalanceIntegrationTest.java` - 2 test D-37

## Decisions Made

- `TransactionWriter` đọc số dư mới bằng native SQL (`findCurrentBalanceNative`) thay vì JPQL `findByIdForUpdate` — tránh Hibernate identity map trả về instance đã cache khi caller cùng transaction đã load ví đó trước.
- Giữ logic tra danh mục hệ thống "Cập nhật số dư" (V8) tại `WalletTransferService.adjustBalance()`, không chuyển vào `TransactionWriter` — đúng ranh giới D-31.
- `@JsonInclude(NON_NULL)` đặt trực tiếp trên 2 record DTO thay vì cấu hình Jackson toàn cục — chỉ 2 DTO này cần hành vi bỏ trường null có chủ đích.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `TransactionWriter.write()` đọc số dư mới bị stale khi caller đã load ví trong cùng transaction**
- **Found during:** Task 2, chạy `mvn test` toàn bộ suite sau khi gộp `WalletTransferService`
- **Issue:** `WalletTransferIntegrationTest` báo sai `new_balance.source_wallet`/`destination_wallet` — `TransactionWriter` gọi `walletRepository.findByIdForUpdate(...)` (JPQL) sau `adjustBalance()` để lấy số dư mới, nhưng `WalletTransferService.transfer()` đã load 2 ví này qua cùng method `findByIdForUpdate` TRƯỚC khi gọi `TransactionWriter` trong cùng `@Transactional`. Hibernate first-level cache (identity map) trả về đúng instance Java đã cache thay vì query lại DB, nên số dư đọc lại vẫn là giá trị TRƯỚC `adjustBalance()`.
- **Fix:** Thêm `WalletRepository.findCurrentBalanceNative` (native SQL `SELECT current_balance FROM wallets WHERE id = :id`, không đi qua entity manager) và đổi `TransactionWriter` dùng method này thay cho `findByIdForUpdate` khi đọc số dư sau ghi.
- **Files modified:** `WalletRepository.java`, `TransactionWriter.java`
- **Commit:** `24ae6d1`

## Issues Encountered

Không có vấn đề nào khác ngoài bug đã tự phát hiện và sửa ở trên. Compile sạch ngay từ Task 1, `mvn test` toàn bộ 79 test PASS sau khi sửa bug ở Task 2.

## User Setup Required

None - không có cấu hình dịch vụ ngoài nào cần thiết lập thủ công.

## Next Phase Readiness

- `TransactionWriter` sẵn sàng làm nền tảng cho `TransactionService` (plan 03-02) và Phase 4 (debt/goal/recurring) — API `TransactionWriteCommand`/`WriteResult` ổn định, không cần sửa lại chữ ký.
- `WalletResponse`/`WalletDetailResponse` đã đúng nghĩa D-37 — plan 03-02/03/04 (CRUD/bulk/sửa-xoá giao dịch) có thể dùng `TransactionWriter` mà không lo lệch nghĩa số dư.
- Không có blocker.

---
*Phase: 03-giao-dich*
*Completed: 2026-08-23*

## Self-Check: PASSED

- FOUND: src/main/java/com/datn/financeapp/transaction/entity/Transaction.java
- FOUND: src/main/java/com/datn/financeapp/transaction/repository/TransactionRepository.java
- FOUND: src/main/java/com/datn/financeapp/transaction/service/TransactionWriter.java
- FOUND: src/main/java/com/datn/financeapp/transaction/service/TransactionWriteCommand.java
- FOUND: src/test/java/com/datn/financeapp/wallet/WalletFutureBalanceIntegrationTest.java
- FOUND: src/main/java/com/datn/financeapp/wallet/repository/WalletRepository.java
- FOUND: src/main/java/com/datn/financeapp/wallet/service/WalletTransferService.java
- FOUND: src/main/java/com/datn/financeapp/wallet/service/WalletService.java
- FOUND: src/main/java/com/datn/financeapp/wallet/dto/WalletResponse.java
- FOUND: src/main/java/com/datn/financeapp/wallet/dto/WalletDetailResponse.java
- FOUND: commit 726fb5d
- FOUND: commit 24ae6d1

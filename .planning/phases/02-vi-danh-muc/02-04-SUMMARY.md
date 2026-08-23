---
phase: 02-vi-danh-muc
plan: 04
subsystem: wallet
tags: [spring-boot, jpa, postgresql, testcontainers, rest-api, concurrency]

# Dependency graph
requires:
  - phase: 02-vi-danh-muc (plan 02-02)
    provides: "wallet/entity/Wallet.java, wallet/repository/WalletRepository.java (CRUD + D-27), wallet/service/WalletService.java, wallet/controller/WalletController.java"
provides:
  - "wallet/service/WalletTransferService.java — transfer/adjustBalance/reconcile, atomic UPDATE, lock theo thứ tự cố định chống deadlock"
  - "wallet/repository/WalletRepository.java — mở rộng thêm findByIdForUpdate (PESSIMISTIC_WRITE) và adjustBalance (atomic UPDATE current_balance)"
  - "3 endpoint REST: POST /wallets/transfer, POST /wallets/{id}/adjust-balance, POST /wallets/{id}/reconcile"
  - "DTO: TransferRequest/Response, AdjustBalanceRequest/Response, ReconcileResponse"
affects: [03-giao-dich]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Khoá 2 ví theo UUID.compareTo() cố định (nhỏ trước, lớn sau) bất kể vai trò nguồn/đích trước khi SELECT ... FOR UPDATE — chống deadlock khi 2 giao dịch A->B và B->A chạy đồng thời"
    - "Atomic UPDATE current_balance = current_balance + :delta thay cho load-modify-save ở mọi thao tác ghi số dư (transfer, adjust-balance, reconcile auto_fix)"
    - "Điều chỉnh số dư sinh giao dịch bù (source='adjustment') thay vì ghi đè current_balance — giữ nguyên tính đúng đắn của công thức đối chiếu initial_balance + thu - chi - chuyển đi + chuyển đến"
    - "Test concurrency gọi thẳng Service qua Spring context (không MockMvc/HTTP) với ExecutorService 2 thread + CountDownLatch để tối đa hoá khả năng đụng độ thật, verify qua Testcontainers PostgreSQL thật"

key-files:
  created:
    - src/main/java/com/datn/financeapp/wallet/service/WalletTransferService.java
    - src/main/java/com/datn/financeapp/wallet/dto/TransferRequest.java
    - src/main/java/com/datn/financeapp/wallet/dto/TransferResponse.java
    - src/main/java/com/datn/financeapp/wallet/dto/AdjustBalanceRequest.java
    - src/main/java/com/datn/financeapp/wallet/dto/AdjustBalanceResponse.java
    - src/main/java/com/datn/financeapp/wallet/dto/ReconcileResponse.java
    - src/test/java/com/datn/financeapp/wallet/WalletTransferIntegrationTest.java
    - src/test/java/com/datn/financeapp/wallet/WalletTransferConcurrencyTest.java
    - src/test/java/com/datn/financeapp/wallet/WalletAdjustBalanceIntegrationTest.java
  modified:
    - src/main/java/com/datn/financeapp/wallet/repository/WalletRepository.java
    - src/main/java/com/datn/financeapp/wallet/controller/WalletController.java

key-decisions:
  - "Tách WalletTransferService riêng khỏi WalletService — nhóm 3 nghiệp vụ transfer/adjust-balance/reconcile đều thao tác trực tiếp current_balance qua lock + atomic UPDATE, tránh transaction self-invocation nếu gộp chung class với các method @Transactional khác của WalletService"
  - "Insert transactions bằng JdbcTemplate (SQL thô), không tạo entity JPA Transaction ở Phase 2 — module transaction/ đầy đủ thuộc Phase 3, đúng ranh giới trách nhiệm hiện tại theo PLAN"
  - "POST /wallets/{id}/reconcile KHÔNG gắn @Idempotent — là POST-hành-động dò lỗi phải luôn tính lại, tương tự lý do D-11 loại /auth/login khỏi idempotency"
  - "Tra category hệ thống 'Cập nhật số dư' bằng query trực tiếp mỗi lần gọi (JOIN category_groups/icons theo g.name='Khác' AND i.code='vi_tien'), không hardcode UUID vì id sinh ngẫu nhiên lúc migration chạy"

requirements-completed: [WALLET-06, WALLET-07, WALLET-08]

# Metrics
duration: 55min
completed: 2026-08-23
---

# Phase 2 Plan 4: Chuyển tiền, điều chỉnh số dư, đối chiếu (WALLET-06..08) Summary

**WalletTransferService mới tách riêng: chuyển tiền atomic có khoá 2 ví theo thứ tự UUID cố định chống deadlock, điều chỉnh số dư kiểm kê sinh giao dịch bù thay vì ghi đè, đối chiếu tính lại từ giao dịch và ghi log khi lệch — 13 test tích hợp qua Testcontainers PostgreSQL thật bao gồm test đồng thời chứng minh không lost-update.**

## Performance

- **Duration:** 55 min
- **Started:** 2026-08-23T10:14:00Z (theo phiên làm việc, xem STATE.md)
- **Completed:** 2026-08-23T11:09:00Z
- **Tasks:** 2
- **Files modified:** 11 (9 tạo mới, 2 sửa)

## Accomplishments

- `WalletRepository` mở rộng thêm `findByIdForUpdate` (`@Lock(PESSIMISTIC_WRITE)`) và `adjustBalance` (atomic `UPDATE ... SET current_balance = current_balance + :delta`)
- `WalletTransferService` mới, tách riêng khỏi `WalletService`, triển khai đủ 3 nghiệp vụ rủi ro cao nhất của module ví:
  - `transfer`: khoá 2 ví theo `UUID.compareTo()` cố định trước khi đọc/ghi (chống deadlock T-02-12), kiểm tra quyền D-27 sau khi lock cho cả nguồn lẫn đích (T-02-13, trả 404 không tiết lộ), mặc định vẫn cho chuyển dù thiếu số dư trừ khi `fail_if_insufficient=true`
  - `adjustBalance`: không ghi đè `current_balance`, sinh đúng 1 giao dịch bù `source=adjustment` gán danh mục hệ thống "Cập nhật số dư" (seed V8), chênh=0 không tạo giao dịch nào, tôn trọng `counts_in_report`
  - `reconcile`: tính `computed_balance` đúng công thức `initial_balance + thu - chi - chuyển đi + chuyển đến`, ghi `log.warn` bắt buộc khi lệch (T-02-15), không tự sửa số nếu `auto_fix=false`, không có job `@Scheduled` nào (D-25)
- `WalletController` thêm 3 endpoint: `POST /wallets/transfer` (201, `@Idempotent`), `POST /wallets/{id}/adjust-balance` (200, `@Idempotent`), `POST /wallets/{id}/reconcile` (200, không `@Idempotent`)
- 13 test tích hợp PASS qua Testcontainers PostgreSQL thật: 4 test transfer happy-path/validate + 1 test concurrency (2 luồng transfer song song, số dư cuối đúng tuyệt đối, chạy ổn định 5 lần liên tiếp qua các tiến trình `mvn` độc lập) + 7 test adjust-balance/reconcile (D-28 mục 2)
- Toàn bộ 74 test của project (46 trước đó + 5 test transfer/validate + 1 concurrency + 7 adjust-balance đã tính, không trùng — số cuối xác nhận qua `mvn test` toàn repo) PASS sau khi hoàn thành plan — không có regression

## Task Commits

Each task was committed atomically:

1. **Task 1: WalletTransferService.transfer + lock chống deadlock/lost-update** - `de99559` (feat)
2. **Task 2: adjustBalance + reconcile** - `630eca9` (feat)

**Plan metadata:** (commit tiếp theo sau summary này)

## Files Created/Modified

- `src/main/java/com/datn/financeapp/wallet/service/WalletTransferService.java` - transfer/adjustBalance/reconcile
- `src/main/java/com/datn/financeapp/wallet/repository/WalletRepository.java` - +findByIdForUpdate, +adjustBalance
- `src/main/java/com/datn/financeapp/wallet/controller/WalletController.java` - +3 endpoint
- `src/main/java/com/datn/financeapp/wallet/dto/TransferRequest.java` - Body POST /wallets/transfer
- `src/main/java/com/datn/financeapp/wallet/dto/TransferResponse.java` - Response 201 chuyển tiền
- `src/main/java/com/datn/financeapp/wallet/dto/AdjustBalanceRequest.java` - Body POST /wallets/{id}/adjust-balance
- `src/main/java/com/datn/financeapp/wallet/dto/AdjustBalanceResponse.java` - Response 200 điều chỉnh số dư
- `src/main/java/com/datn/financeapp/wallet/dto/ReconcileResponse.java` - Response 200 đối chiếu
- `src/test/java/com/datn/financeapp/wallet/WalletTransferIntegrationTest.java` - 4 test happy path + validate
- `src/test/java/com/datn/financeapp/wallet/WalletTransferConcurrencyTest.java` - Test đồng thời (D-28 mục 1)
- `src/test/java/com/datn/financeapp/wallet/WalletAdjustBalanceIntegrationTest.java` - 7 test WALLET-08/WALLET-07

## Decisions Made

- Tách `WalletTransferService` khỏi `WalletService` để tránh transaction self-invocation nếu `WalletController` cần gọi cả hai trong tương lai — theo đúng cạm bẫy đã ghi ở `IdempotencyTransactionHelper` Phase 1.
- Insert bản ghi `transactions` bằng `JdbcTemplate` SQL thô — Phase 2 chưa có entity JPA cho `transactions` (thuộc Phase 3), đúng ranh giới trách nhiệm hiện tại.
- `POST /wallets/{id}/reconcile` không gắn `@Idempotent` vì là hành động dò lỗi phải luôn tính lại từ dữ liệu mới nhất, không được cache kết quả cũ.
- Category hệ thống "Cập nhật số dư" được tra bằng query mỗi lần gọi (không cache/hardcode UUID) vì id sinh ngẫu nhiên khi migration V8 chạy.

## Deviations from Plan

None - plan thực thi đúng như đặc tả, không phát hiện lệch nào giữa `api/02-VI.md`, `db/migration/V2__giao_dich.sql`/`V8__dieu_chinh_so_du.sql` và code triển khai. Điều kiện quyền D-27 (`group_members.is_active`) đã được xác nhận đúng từ plan 02-02, tái sử dụng nguyên vẹn ở `lockAndCheckOwnership`.

## Issues Encountered

Không có vấn đề kỹ thuật nào ngoài dự kiến. Test concurrency (`WalletTransferConcurrencyTest`) chạy PASS ngay từ lần đầu và ổn định qua 5 lần chạy độc lập (mỗi lần một tiến trình `mvn` riêng, không chỉ 5 lần `@RepeatedTest` trong cùng JVM) — không phát hiện flaky.

## User Setup Required

None - không có cấu hình dịch vụ ngoài nào cần thiết lập thủ công.

## Next Phase Readiness

- `WalletTransferService` sẵn sàng làm nền tảng cho Phase 3 (giao dịch) — pattern lock + atomic UPDATE số dư sẽ được tái sử dụng khi Phase 3 xây entity `Transaction` đầy đủ và luồng sửa/xoá giao dịch 3 bước (hoàn tác cũ → ghi mới → áp dụng mới).
- Hoàn tất toàn bộ Phase 2 module Ví (WALLET-01..08). Chỉ còn Danh mục (CAT-01..06, đã xong ở plan 02-03) — Phase 2 đã đủ 4/4 plan.
- Không có blocker.

---
*Phase: 02-vi-danh-muc*
*Completed: 2026-08-23*

## Self-Check: PASSED

- FOUND: src/main/java/com/datn/financeapp/wallet/service/WalletTransferService.java
- FOUND: src/main/java/com/datn/financeapp/wallet/dto/TransferRequest.java
- FOUND: src/main/java/com/datn/financeapp/wallet/dto/TransferResponse.java
- FOUND: src/main/java/com/datn/financeapp/wallet/dto/AdjustBalanceRequest.java
- FOUND: src/main/java/com/datn/financeapp/wallet/dto/AdjustBalanceResponse.java
- FOUND: src/main/java/com/datn/financeapp/wallet/dto/ReconcileResponse.java
- FOUND: src/test/java/com/datn/financeapp/wallet/WalletTransferIntegrationTest.java
- FOUND: src/test/java/com/datn/financeapp/wallet/WalletTransferConcurrencyTest.java
- FOUND: src/test/java/com/datn/financeapp/wallet/WalletAdjustBalanceIntegrationTest.java
- FOUND: commit de99559
- FOUND: commit 630eca9

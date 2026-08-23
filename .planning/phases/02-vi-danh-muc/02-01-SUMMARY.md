---
phase: 02-vi-danh-muc
plan: 01
subsystem: wallet
tags: [jpa, hibernate, postgresql, refactor, spring-boot]

# Dependency graph
requires:
  - phase: 01-nen-tang-xac-thuc
    provides: "AuthService.register() tạo user + ví Tiền mặt qua WalletMinimal (entity tạm thời)"
provides:
  - "wallet/entity/Wallet.java — entity đầy đủ 12 cột bảng wallets, thay thế WalletMinimal"
  - "wallet/repository/WalletRepository.java — repository JPA tối thiểu (countByUserIdAndIsDeletedFalse), sẵn sàng mở rộng CRUD ở plan 02-02"
affects: [02-02-wallet-crud, 02-03-category, 02-04]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Entity ví đặt tại wallet/entity/, repository tại wallet/repository/ theo package-by-feature"
    - "Giữ columnDefinition=bpchar(7) cho cột color CHAR(7) — cạm bẫy Hibernate ddl-auto=validate đã ghi nhận từ Phase 1"

key-files:
  created:
    - src/main/java/com/datn/financeapp/wallet/entity/Wallet.java
    - src/main/java/com/datn/financeapp/wallet/repository/WalletRepository.java
  modified:
    - src/main/java/com/datn/financeapp/auth/service/AuthService.java
    - src/test/java/com/datn/financeapp/auth/AuthRegisterLoginIntegrationTest.java
    - src/test/java/com/datn/financeapp/auth/AuthLoginLockoutIntegrationTest.java
    - src/test/java/com/datn/financeapp/auth/AuthProfilePasswordIntegrationTest.java
    - src/test/java/com/datn/financeapp/auth/AuthRefreshRotationIntegrationTest.java
    - src/test/java/com/datn/financeapp/auth/AuthIdempotencyRateLimitEndToEndTest.java

key-decisions:
  - "Copy nguyên trạng WalletMinimal sang Wallet, không thêm field mới ở plan này (sortOrder đã có sẵn từ Phase 1)"
  - "WalletRepository chỉ giữ 1 method tối thiểu ở plan này — plan 02-02 mở rộng thêm CRUD/quyền vào cùng file, không tạo file mới"

patterns-established:
  - "Pattern: khi thay thế entity tạm thời, xoá hẳn package cũ trong cùng plan (không giữ song song hai entity ánh xạ cùng bảng) để tránh Hibernate giữ hai persistence context khác nhau cho cùng dữ liệu"

requirements-completed: []

# Metrics
duration: 7min
completed: 2026-08-23
---

# Phase 2 Plan 1: Thay thế WalletMinimal bằng Wallet entity đầy đủ Summary

**Entity `Wallet` đầy đủ 12 cột thay thế hẳn `WalletMinimal` tạm thời của Phase 1, `AuthService` và 5 file test integration auth chuyển sang dùng `WalletRepository`, 35/35 test Phase 1 vẫn xanh.**

## Performance

- **Duration:** 7 min
- **Started:** 2026-08-23T09:47:04Z
- **Completed:** 2026-08-23T09:54:38Z
- **Tasks:** 2
- **Files modified:** 8 (2 tạo mới, 2 xoá, 6 sửa)

## Accomplishments
- Tạo `wallet/entity/Wallet.java` và `wallet/repository/WalletRepository.java` — nền tảng cho toàn bộ nghiệp vụ ví ở các plan sau của Phase 2
- Xoá hoàn toàn package `common/wallet/` (`WalletMinimal`, `WalletMinimalRepository`) — không còn hai entity song song ánh xạ bảng `wallets`
- `AuthService.register()`/`getMe()` và 5 file test integration auth chuyển sang `Wallet`/`WalletRepository`, giữ nguyên toàn bộ logic nghiệp vụ và assertion
- Xác nhận `mvn test`: đúng 35/35 test Phase 1 PASS, 0 failures, 0 errors

## Task Commits

Each task was committed atomically:

1. **Task 1: Tạo entity Wallet + WalletRepository tối thiểu, xoá common/wallet/** - `ee76950` (feat)
2. **Task 2: Sửa AuthService + 5 file test Phase 1, chạy lại toàn bộ 35 test xanh** - `53e52ad` (feat)

**Plan metadata:** (commit tiếp theo sau summary này)

## Files Created/Modified
- `src/main/java/com/datn/financeapp/wallet/entity/Wallet.java` - Entity JPA đầy đủ cho bảng `wallets`, thay `WalletMinimal`
- `src/main/java/com/datn/financeapp/wallet/repository/WalletRepository.java` - Repository JPA tối thiểu, sẽ mở rộng ở plan 02-02
- `src/main/java/com/datn/financeapp/auth/service/AuthService.java` - `register()`/`getMe()` dùng `Wallet`/`WalletRepository`
- `src/test/java/com/datn/financeapp/auth/AuthRegisterLoginIntegrationTest.java` - import/field/assertion đổi sang `walletRepository`
- `src/test/java/com/datn/financeapp/auth/AuthLoginLockoutIntegrationTest.java` - import/field/deleteAll đổi sang `walletRepository`
- `src/test/java/com/datn/financeapp/auth/AuthProfilePasswordIntegrationTest.java` - import/field/deleteAll đổi sang `walletRepository`
- `src/test/java/com/datn/financeapp/auth/AuthRefreshRotationIntegrationTest.java` - import/field/deleteAll đổi sang `walletRepository`
- `src/test/java/com/datn/financeapp/auth/AuthIdempotencyRateLimitEndToEndTest.java` - import/field/deleteAll đổi sang `walletRepository`

(Hai file `common/wallet/WalletMinimal.java` và `common/wallet/WalletMinimalRepository.java` đã bị xoá — không còn trên đĩa, thư mục `common/wallet/` không còn tồn tại.)

## Decisions Made
- Copy nguyên trạng cấu trúc `WalletMinimal` sang `Wallet` (đủ 12 field, giữ `columnDefinition = "bpchar(7)"` cho cột `color`) — không thêm field mới ở plan này vì `sortOrder` đã tồn tại sẵn trong `WalletMinimal`.
- `WalletRepository` chỉ giữ method `countByUserIdAndIsDeletedFalse` ở plan này; plan 02-02 sẽ mở rộng thêm method CRUD/quyền vào cùng file, không tạo repository mới.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

Không có vấn đề phát sinh. `mvn test` chạy đúng một lần, toàn bộ 35 test PASS ngay từ đầu (không cần fix lặp lại).

## User Setup Required

None - không có cấu hình dịch vụ ngoài nào cần thiết lập thủ công.

## Next Phase Readiness

- `wallet/entity/Wallet.java` và `wallet/repository/WalletRepository.java` sẵn sàng để plan 02-02 (Wallet CRUD) mở rộng thêm method truy vấn/quyền vào cùng repository, không tạo file mới.
- Không còn rủi ro "hai entity cùng ánh xạ bảng wallets" — an toàn để xây tiếp nghiệp vụ ví (CRUD, chuyển tiền, đối chiếu, điều chỉnh số dư) lên trên entity `Wallet` duy nhất.
- Không có blocker.

---
*Phase: 02-vi-danh-muc*
*Completed: 2026-08-23*

## Self-Check: PASSED

- FOUND: src/main/java/com/datn/financeapp/wallet/entity/Wallet.java
- FOUND: src/main/java/com/datn/financeapp/wallet/repository/WalletRepository.java
- OK: src/main/java/com/datn/financeapp/common/wallet/ đã bị xoá
- FOUND: commit ee76950
- FOUND: commit 53e52ad

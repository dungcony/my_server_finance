---
phase: 02-vi-danh-muc
plan: 02
subsystem: wallet
tags: [spring-boot, jpa, postgresql, testcontainers, rest-api]

# Dependency graph
requires:
  - phase: 02-vi-danh-muc (plan 02-01)
    provides: "wallet/entity/Wallet.java + wallet/repository/WalletRepository.java tối thiểu, thay WalletMinimal"
provides:
  - "wallet/repository/WalletRepository.java — mở rộng đầy đủ: findByIdForUser/findByIdForUserIncludingDeleted (điều kiện quyền D-27), findAllForUser, existsByUserIdAndNameIgnoreCase, findMaxSortOrderByUserId, updateSortOrder"
  - "wallet/service/WalletService.java — list/summary/detail/create/update/delete/reorder theo api/02-VI.md mục 1-7"
  - "wallet/controller/WalletController.java — 7 endpoint REST CRUD ví"
  - "DTO đầy đủ: CreateWalletRequest, UpdateWalletRequest (chặn mass-assignment current_balance/type), WalletResponse, WalletDetailResponse, WalletStatsDto, WalletSummaryResponse, ReorderWalletsRequest"
affects: [02-03-danh-muc, 02-04-chuyen-tien-doi-chieu, 03-giao-dich]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Điều kiện quyền D-27 (user_id = :currentUser OR group_id IN (SELECT group_id FROM group_members WHERE user_id = :currentUser AND is_active)) viết trực tiếp trong native @Query của repository — KHÔNG dùng status = 'active' (cột không tồn tại trong schema thật)"
    - "UpdateWalletRequest dùng @JsonAnySetter gom field lạ vào map để bắt mass-assignment tường minh (trả lỗi rõ ràng) thay vì để Jackson âm thầm bỏ qua field DTO không khai báo"
    - "findByIdForUser (lọc is_deleted) dùng cho GET/PATCH; findByIdForUserIncludingDeleted (không lọc) dùng riêng cho DELETE để giữ tính idempotent CORE-06"

key-files:
  created:
    - src/main/java/com/datn/financeapp/wallet/controller/WalletController.java
    - src/main/java/com/datn/financeapp/wallet/service/WalletService.java
    - src/main/java/com/datn/financeapp/wallet/dto/CreateWalletRequest.java
    - src/main/java/com/datn/financeapp/wallet/dto/UpdateWalletRequest.java
    - src/main/java/com/datn/financeapp/wallet/dto/WalletResponse.java
    - src/main/java/com/datn/financeapp/wallet/dto/WalletDetailResponse.java
    - src/main/java/com/datn/financeapp/wallet/dto/WalletStatsDto.java
    - src/main/java/com/datn/financeapp/wallet/dto/WalletSummaryResponse.java
    - src/main/java/com/datn/financeapp/wallet/dto/ReorderWalletsRequest.java
    - src/test/java/com/datn/financeapp/wallet/WalletCrudIntegrationTest.java
    - src/test/java/com/datn/financeapp/wallet/WalletAccessControlIntegrationTest.java
  modified:
    - src/main/java/com/datn/financeapp/wallet/repository/WalletRepository.java
    - src/test/java/com/datn/financeapp/common/exception/GlobalExceptionHandlerTest.java

key-decisions:
  - "Sửa điều kiện quyền D-27 dùng group_members.is_active (BOOLEAN) thay vì status = 'active' như PLAN/CONTEXT mô tả — đối chiếu db/migration/V1__nen_tang.sql cho thấy cột status không tồn tại trong schema thật (CLAUDE.md quy tắc 0)"
  - "DELETE ví dùng repository method riêng không lọc is_deleted để phân biệt 404 (không tồn tại/không có quyền) với no-op 200 (đã xoá mềm từ trước, gọi lại idempotent theo CORE-06)"
  - "Group_id != null trong POST /wallets luôn ném NOT_GROUP_MEMBER (403) — Group thuộc Phase 5, chưa có cách kiểm chứng thành viên nhóm"

patterns-established:
  - "Repository D-27: mọi @Query chọn/sửa/xoá MỘT bản ghi theo id kèm điều kiện quyền ngay trong native SQL, không load hết rồi lọc Java"
  - "@WebMvcTest slice test (GlobalExceptionHandlerTest) phải liệt kê tường minh mọi Controller nghiệp vụ mới vào excludeFilters — nếu không Spring component-scan kéo controller vào context thiếu Service bean"

requirements-completed: [WALLET-01, WALLET-02, WALLET-03, WALLET-04, WALLET-05]

# Metrics
duration: 40min
completed: 2026-08-23
---

# Phase 2 Plan 2: CRUD ví (WALLET-01..05) Summary

**7 endpoint REST CRUD ví (list/summary/detail/create/update/delete/reorder) với điều kiện quyền D-27 trong mọi native query, chặn mass-assignment current_balance/type qua PATCH, 11 test tích hợp qua Testcontainers PostgreSQL thật (bao gồm test 404 quyền truy cập D-28 mục 3).**

## Performance

- **Duration:** 40 min
- **Started:** 2026-08-23T09:56:00Z
- **Completed:** 2026-08-23T10:10:05Z
- **Tasks:** 2
- **Files modified:** 13 (11 tạo mới, 2 sửa)

## Accomplishments
- `WalletRepository` mở rộng đầy đủ với điều kiện quyền D-27 trong mọi query chọn/sửa/xoá một ví theo id
- `WalletService` triển khai đủ 7 nghiệp vụ theo `api/02-VI.md` mục 1-7: list, summary (personal/shared không cộng đôi), detail (kèm stats), create (không sinh giao dịch), update (chặn sửa balance/type), delete (mềm, idempotent, chặn xoá ví cuối/ví còn giao dịch), reorder
- `WalletController` expose 7 endpoint, chỉ `POST /wallets` gắn `@Idempotent` đúng D-11
- 11 test tích hợp PASS qua Testcontainers PostgreSQL thật: 8 test CRUD happy-path (`WalletCrudIntegrationTest`) + 3 test quyền truy cập 404 (`WalletAccessControlIntegrationTest`, D-28 mục 3)
- Toàn bộ 46 test của project (35 Phase 1 + 11 Phase 2) PASS sau khi hoàn thành plan — không có regression

## Task Commits

Each task was committed atomically:

1. **Task 1: WalletRepository (mở rộng) + WalletService — CRUD + validate nghiệp vụ** - `4f8931f` (feat)
2. **Task 2: WalletController + test CRUD happy-path + test quyền truy cập 404** - `1ac48cf` (feat, bao gồm fix bug D-27 phát hiện khi chạy test thật)

**Plan metadata:** (commit tiếp theo sau summary này)

## Files Created/Modified
- `src/main/java/com/datn/financeapp/wallet/repository/WalletRepository.java` - Mở rộng đầy đủ method CRUD/quyền D-27
- `src/main/java/com/datn/financeapp/wallet/service/WalletService.java` - Business logic 7 nghiệp vụ ví
- `src/main/java/com/datn/financeapp/wallet/controller/WalletController.java` - 7 endpoint REST
- `src/main/java/com/datn/financeapp/wallet/dto/CreateWalletRequest.java` - Body POST /wallets
- `src/main/java/com/datn/financeapp/wallet/dto/UpdateWalletRequest.java` - Body PATCH /wallets/{id}, chặn mass-assignment
- `src/main/java/com/datn/financeapp/wallet/dto/WalletResponse.java` - Phần tử GET /wallets
- `src/main/java/com/datn/financeapp/wallet/dto/WalletDetailResponse.java` - GET /wallets/{id}
- `src/main/java/com/datn/financeapp/wallet/dto/WalletStatsDto.java` - stats lồng trong detail
- `src/main/java/com/datn/financeapp/wallet/dto/WalletSummaryResponse.java` - GET /wallets/summary
- `src/main/java/com/datn/financeapp/wallet/dto/ReorderWalletsRequest.java` - Body PATCH /wallets/reorder
- `src/test/java/com/datn/financeapp/wallet/WalletCrudIntegrationTest.java` - 8 test CRUD happy-path
- `src/test/java/com/datn/financeapp/wallet/WalletAccessControlIntegrationTest.java` - 3 test quyền truy cập 404
- `src/test/java/com/datn/financeapp/common/exception/GlobalExceptionHandlerTest.java` - Loại trừ WalletController khỏi @WebMvcTest slice

## Decisions Made
- `group_members.is_active` (BOOLEAN) thay cho `status = 'active'` như PLAN/CONTEXT mô tả — schema thật (`db/migration/V1__nen_tang.sql`) không có cột `status` trên bảng này. Đã sửa cả `WalletRepository` và `WalletService.summary()`.
- `DELETE /wallets/{id}` dùng `findByIdForUserIncludingDeleted` (không lọc `is_deleted`) để phân biệt "không tồn tại/không có quyền" (404) với "đã xoá mềm rồi, idempotent trả về bình thường" (CORE-06).
- `POST /wallets` với `group_id != null` luôn ném `NOT_GROUP_MEMBER` (403) — Group thuộc Phase 5, chưa có cách kiểm chứng thành viên nhóm ở Phase 2.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Điều kiện quyền D-27 dùng sai tên cột `group_members.status`**
- **Found during:** Task 2 (chạy `WalletCrudIntegrationTest`/`WalletAccessControlIntegrationTest` qua Testcontainers PostgreSQL thật)
- **Issue:** `WalletRepository` và `WalletService.summary()` viết `group_members WHERE user_id = ? AND status = 'active'` theo đúng mẫu trong PLAN/CONTEXT — nhưng bảng `group_members` thật (V1__nen_tang.sql) chỉ có cột `is_active BOOLEAN`, không có cột `status`. Mọi request GET/PATCH/DELETE /wallets/{id} và GET /wallets/summary trả về 500 `INTERNAL_ERROR` (Postgres báo `column "status" does not exist`).
- **Fix:** Đổi toàn bộ `AND status = 'active'` thành `AND is_active` trong 3 native query của `WalletRepository` và 2 câu JdbcTemplate của `WalletService.summary()`. Cập nhật javadoc ghi rõ lưu ý đối chiếu schema thật.
- **Files modified:** `wallet/repository/WalletRepository.java`, `wallet/service/WalletService.java`
- **Verification:** `WalletCrudIntegrationTest` (8 test) và `WalletAccessControlIntegrationTest` (3 test) đều PASS sau khi sửa
- **Committed in:** `1ac48cf` (Task 2 commit)

**2. [Rule 3 - Blocking] `cleanTables()` của `WalletCrudIntegrationTest` vi phạm FK `fk_txn_wallet`**
- **Found during:** Task 2 (chạy test `deleteWallet_hasTransactionsWithoutFlag_returns409WalletHasTransactions` chèn thẳng transaction giả qua JDBC)
- **Issue:** `@BeforeEach cleanTables()` gọi `walletRepository.deleteAll()` trước khi xoá `transactions` — vi phạm FK `fk_txn_wallet` vì bảng `transactions` (đã tồn tại từ V2) tham chiếu `wallets`.
- **Fix:** Thêm `jdbcTemplate.update("DELETE FROM transactions")` TRƯỚC `walletRepository.deleteAll()`.
- **Files modified:** `src/test/java/com/datn/financeapp/wallet/WalletCrudIntegrationTest.java`
- **Verification:** Toàn bộ 8 test trong file chạy tuần tự không còn lỗi `DataIntegrityViolationException`
- **Committed in:** `1ac48cf` (Task 2 commit)

**3. [Rule 3 - Blocking] `WalletController` mới làm vỡ `GlobalExceptionHandlerTest` (@WebMvcTest slice)**
- **Found during:** Task 2 (chạy toàn bộ `mvn test` sau khi thêm `WalletController`)
- **Issue:** `GlobalExceptionHandlerTest` dùng `@WebMvcTest` không chỉ định `controllers=`, Spring tự component-scan mọi `@RestController` trong classpath — `WalletController` mới bị kéo vào context slice này nhưng `WalletService` (bean phụ thuộc) không tồn tại trong slice, gây `UnsatisfiedDependencyException` ở cả 3 test case.
- **Fix:** Thêm `com.datn.financeapp.wallet.controller.WalletController.class` vào `excludeFilters` của `GlobalExceptionHandlerTest`, cùng mẫu đã áp dụng cho `AuthController` ở Phase 1.
- **Files modified:** `src/test/java/com/datn/financeapp/common/exception/GlobalExceptionHandlerTest.java`
- **Verification:** `mvn test -Dtest=GlobalExceptionHandlerTest` PASS 3/3; `mvn test` toàn bộ 46 test PASS
- **Committed in:** `1ac48cf` (Task 2 commit)

---

**Total deviations:** 3 auto-fixed (1 bug nghiêm trọng ở tầng quyền, 2 blocking ở test)
**Impact on plan:** Cả 3 fix cần thiết để plan hoạt động đúng và không phá vỡ Phase 1 — deviation #1 đặc biệt quan trọng vì nếu không phát hiện, mọi endpoint GET/PATCH/DELETE ví theo id và GET /wallets/summary sẽ 500 ngay khi chạy thật, che khuất luôn test quyền truy cập D-28 mục 3 (test tưởng PASS nhưng thực ra fail ở tầng SQL trước khi tới logic quyền). Không có scope creep — cả 3 đều là sửa lỗi đúng phạm vi task.

## Issues Encountered

Không có vấn đề nào khác ngoài 3 deviation đã ghi ở trên — toàn bộ được phát hiện và sửa trong quá trình chạy test tích hợp qua Testcontainers thật (không phải test giả lập), đúng tinh thần D-23/D-28 của Phase 2.

## User Setup Required

None - không có cấu hình dịch vụ ngoài nào cần thiết lập thủ công.

## Next Phase Readiness

- `WalletService`/`WalletController` sẵn sàng cho plan 02-04 (chuyển tiền, điều chỉnh số dư, đối chiếu) tái sử dụng `WalletRepository.findByIdForUser` và pattern điều kiện quyền D-27 đã xác nhận đúng với schema thật.
- Danh sách ví hợp lệ (GET /wallets) đã sẵn sàng làm nền cho module giao dịch ở Phase 3 (gán `wallet_id` vào transaction).
- **Lưu ý quan trọng cho plan 02-03/02-04 và mọi plan sau dùng `group_members`:** cột lọc thành viên hoạt động là `is_active BOOLEAN`, KHÔNG phải `status`. PLAN/CONTEXT của Phase 2 mô tả sai theo `status = 'active'` — nên đối chiếu `db/migration/V1__nen_tang.sql` trực tiếp thay vì copy lại mẫu SQL trong tài liệu planning.
- Không có blocker.

---
*Phase: 02-vi-danh-muc*
*Completed: 2026-08-23*

## Self-Check: PASSED

- FOUND: src/main/java/com/datn/financeapp/wallet/controller/WalletController.java
- FOUND: src/main/java/com/datn/financeapp/wallet/service/WalletService.java
- FOUND: src/main/java/com/datn/financeapp/wallet/repository/WalletRepository.java
- FOUND: src/main/java/com/datn/financeapp/wallet/dto/CreateWalletRequest.java
- FOUND: src/main/java/com/datn/financeapp/wallet/dto/UpdateWalletRequest.java
- FOUND: src/main/java/com/datn/financeapp/wallet/dto/WalletResponse.java
- FOUND: src/main/java/com/datn/financeapp/wallet/dto/WalletDetailResponse.java
- FOUND: src/main/java/com/datn/financeapp/wallet/dto/WalletStatsDto.java
- FOUND: src/main/java/com/datn/financeapp/wallet/dto/WalletSummaryResponse.java
- FOUND: src/main/java/com/datn/financeapp/wallet/dto/ReorderWalletsRequest.java
- FOUND: src/test/java/com/datn/financeapp/wallet/WalletCrudIntegrationTest.java
- FOUND: src/test/java/com/datn/financeapp/wallet/WalletAccessControlIntegrationTest.java
- FOUND: commit 4f8931f
- FOUND: commit 1ac48cf

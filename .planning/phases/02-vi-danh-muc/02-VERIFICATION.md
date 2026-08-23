---
phase: 02-vi-danh-muc
verified: 2026-08-23T11:10:00Z
status: passed
score: 6/6 must-haves verified (14/14 requirement ID truy vết)
overrides_applied: 0
---

# Phase 2: Ví & Danh mục Verification Report

**Phase Goal:** User quản lý đầy đủ ví tiền (kể cả chuyển tiền giữa ví, đối chiếu số dư) và cây danh mục 2 cấp gắn icon, làm nền cho module giao dịch ở phase sau.
**Verified:** 2026-08-23T11:10:00Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (6 Success Criteria của ROADMAP.md)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | CRUD ví: không sửa `current_balance`/`type` qua PATCH; không xoá ví cuối/ví còn giao dịch nếu chưa xác nhận `delete_transactions` | ✓ VERIFIED | `UpdateWalletRequest` không có field `currentBalance`/`type`; `WalletService.update` bắt `extraFields` chứa `current_balance`/`type` → `BALANCE_NOT_EDITABLE` (400). `WalletService.delete`: `countByUserIdAndIsDeletedFalse <= 1` → `CANNOT_DELETE_LAST_WALLET`; còn giao dịch mà không `deleteTransactions` → `WALLET_HAS_TRANSACTIONS`. Test `WalletCrudIntegrationTest` (8 test) PASS |
| 2 | Chuyển tiền: 1 DB transaction, đúng 1 bản ghi `type=transfer`, mặc định cho phép chuyển dù thiếu số dư trừ khi `fail_if_insufficient=true` | ✓ VERIFIED | `WalletTransferService.transfer` — 1 INSERT `transactions` (`type='transfer'`), atomic `adjustBalance` 2 lần trong cùng `@Transactional`. Mặc định `failIfInsufficient` null/false vẫn cho chuyển; `true` + thiếu số dư → `INSUFFICIENT_BALANCE` 422. Test `WalletTransferIntegrationTest` (5 test) PASS |
| 3 | Đối chiếu đúng công thức `initial_balance + thu - chi - chuyển đi + chuyển đến`, ghi log khi lệch | ✓ VERIFIED | `WalletTransferService.reconcile` — câu SQL đúng công thức (dòng 169-179 file thật), `log.warn` khi `difference != 0` (dòng 189-192), không tự sửa khi `autoFix=false` (test `reconcile_detectsDrift_doesNotSilentlyFixWhenAutoFixFalse` PASS). Không có `@Scheduled` job (đúng D-25 — `grep -rn "@Scheduled" wallet/` rỗng) |
| 4 | Danh mục tối đa 2 tầng, con cùng `type` với cha; không xoá còn con/giao dịch/ngân sách; hệ thống bất biến | ✓ VERIFIED | `CategoryService.create`/`update` validate `parent.getParentCategoryId()==null` (MAX_DEPTH_EXCEEDED), `parent.getType().equals(req.type())` (TYPE_MISMATCH_WITH_PARENT); `delete` chặn `CHILD_CATEGORIES_EXIST`/`CATEGORY_HAS_TRANSACTIONS`; `userId==null` → `SYSTEM_CATEGORY_NOT_EDITABLE`/`NOT_DELETABLE`. Trigger DB `fn_categories_validate` là lớp chặn cuối (fallback qua `mapDatabaseCheckViolation`). Test `CategoryTreeIntegrationTest` (8 test) PASS |
| 5 | API nhóm lớn + kho icon lọc theo `icon_group`/`search` | ✓ VERIFIED | `GET /category-groups` → `listCategoryGroups()`; `GET /icons?icon_group=&search=` → `IconRepository.search` với `LIKE` trên `display_name`/`search_keywords`. `CategoryController` expose đủ 8 endpoint |
| 6 | WALLET-08: tạo đúng 1 giao dịch bù (`source=adjustment`), KHÔNG ghi đè `current_balance`; chênh=0 không tạo giao dịch | ✓ VERIFIED | `WalletTransferService.adjustBalance`: nhánh `difference==0` return sớm không INSERT (dòng 124-127); còn lại `walletRepository.adjustBalance(walletId, difference)` — atomic UPDATE cộng chênh, không set thẳng `actualBalance`. Test `WalletAdjustBalanceIntegrationTest` đủ cả 7 behavior (2 chiều, chênh=0, âm, `counts_in_report`, reconcile sau điều chỉnh, phát hiện lệch) PASS |

**Score:** 6/6 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `wallet/entity/Wallet.java` | Entity đầy đủ 12 cột thay `WalletMinimal` | ✓ VERIFIED | Đủ field, `bpchar(7)` giữ nguyên cho `color`, `common/wallet/` đã xoá hoàn toàn (`find` rỗng), `grep -rn "WalletMinimal" src/` chỉ còn 1 dòng Javadoc mô tả lịch sử (không phải code tham chiếu) |
| `wallet/repository/WalletRepository.java` | CRUD + D-27 + lock + atomic update | ✓ VERIFIED | `findByIdForUser` có điều kiện quyền D-27; `findByIdForUpdate` dùng `PESSIMISTIC_WRITE`; `adjustBalance` atomic UPDATE |
| `wallet/service/WalletService.java` | CRUD ví + validate | ✓ VERIFIED | Đủ `list/summary/detail/create/update/delete/reorder`, đúng mã lỗi `WALLET_NAME_EXISTS/BALANCE_NOT_EDITABLE/WALLET_HAS_TRANSACTIONS/CANNOT_DELETE_LAST_WALLET` |
| `wallet/service/WalletTransferService.java` | Transfer/adjust-balance/reconcile atomic | ✓ VERIFIED | Khoá 2 ví theo `compareTo()`, atomic update, ghi log reconcile, đúng nghiệp vụ adjustment |
| `wallet/controller/WalletController.java` | 10 endpoint | ✓ VERIFIED | 7 CRUD + transfer + adjust-balance + reconcile, `@Idempotent` đúng 3 chỗ (POST /wallets, POST /wallets/transfer, POST /wallets/{id}/adjust-balance) |
| `category/service/CategoryService.java` | CRUD 2 cấp + 5 ràng buộc | ✓ VERIFIED | Đủ `MAX_DEPTH_EXCEEDED/TYPE_MISMATCH_WITH_PARENT/CATEGORY_GROUP_REQUIRED/CATEGORY_NAME_EXISTS/INVALID_ICON/SYSTEM_CATEGORY_NOT_EDITABLE/SYSTEM_CATEGORY_NOT_DELETABLE/CATEGORY_HAS_CHILDREN/CHILD_CATEGORIES_EXIST/CATEGORY_HAS_TRANSACTIONS` — 10 mã lỗi |
| `category/repository/CategoryRepository.java` | `fn_category_tree` dùng chung | ✓ VERIFIED | `findCategoryTree` gọi native `fn_category_tree(:categoryId)`, không lặp điều kiện lọc cây ở nơi khác |
| `category/controller/CategoryController.java` | 8 endpoint | ✓ VERIFIED | GET/POST/PATCH/DELETE categories + reorder + category-groups + icons, `@Idempotent` chỉ ở POST /categories |
| `WalletAccessControlIntegrationTest.java` | Test 404 quyền ví | ✓ VERIFIED | 3 test case (GET/PATCH/DELETE ví người khác) PASS |
| `CategoryAccessControlIntegrationTest.java` | Test 404 quyền danh mục + fix HG-01/HG-02 | ✓ VERIFIED | 6 test case, gồm `userA_createChildUnderUserBPrivateParent_returns404NotFound` và `userA_patchOwnCategoryParentToUserBPrivateCategory_returns404NotFound` |
| `WalletTransferConcurrencyTest.java` | Test lost-update | ✓ VERIFIED | `@RepeatedTest(5)` — chạy 5 lần, số dư cuối đúng tuyệt đối (6tr/4tr) |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `AuthService` | `WalletRepository` | field injection thay `WalletMinimalRepository` | ✓ WIRED | `AuthService.register()`/`getMe()` dùng `walletRepository`, không còn `WalletMinimal` |
| `WalletController` | `WalletRepository` (qua Service) | D-27 điều kiện quyền trong `@Query` | ✓ WIRED | `w.user_id = :currentUser OR w.group_id IN (...)` xuất hiện ở mọi query select-1-ví |
| `WalletTransferService` | `WalletRepository.adjustBalance` | atomic UPDATE `current_balance = current_balance + :delta` | ✓ WIRED | Dùng cho cả transfer (2 lần) và adjustBalance (1 lần), không `setCurrentBalance` (`grep -c setCurrentBalance` = 0) |
| `WalletTransferService.adjustBalance` | Danh mục hệ thống V8 | tra `category_group.name='Khác' + icon.code='vi_tien'` | ✓ WIRED | Query JOIN đúng 3 bảng, `IllegalStateException` fallback nếu thiếu seed |
| `CategoryRepository` | `fn_category_tree` (V6) | native `@Query` | ✓ WIRED | Method tồn tại, sẵn sàng cho Phase 3/4 dùng lại |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `WalletService.summary` | `personalTotal/sharedTotal` | `JdbcTemplate` SUM trên `wallets` thật | Có — test `WalletCrudIntegrationTest` xác nhận không cộng đôi | ✓ FLOWING |
| `WalletTransferService.reconcile` | `computedBalance` | `JdbcTemplate` SUM trên `transactions` thật theo công thức đầy đủ | Có — test `afterAdjustment_reconcileStillMatches` chứng minh khớp sau điều chỉnh | ✓ FLOWING |
| `CategoryService.list` (as_tree) | cây 2 cấp | `findAllForUser` trên `categories` thật, group bằng Java | Có — test `getCategoriesAsTree_returnsSystemAndUserCategoriesInTwoLevelTree` PASS | ✓ FLOWING |

### Behavioral Spot-Checks

Không chạy spot-check thủ công (curl/CLI) vì đã có bộ test tích hợp Testcontainers PostgreSQL thật bao phủ đầy đủ hành vi (77/77 test PASS qua `mvn test`), tương đương hoặc mạnh hơn spot-check thủ công. `mvn test` xác nhận:

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Toàn bộ test suite Phase 1 + Phase 2 | `mvn -q test` | 77/77 PASS, `Failures: 0, Errors: 0, Skipped: 0` (surefire reports, 17 file test) | ✓ PASS |
| Concurrency không flaky | `@RepeatedTest(5)` trong `WalletTransferConcurrencyTest` | 5/5 lần PASS trong cùng lần chạy `mvn test` | ✓ PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|---|---|---|---|---|
| WALLET-01 | 02-02 | Xem danh sách/tổng tài sản, không cộng đôi | ✓ SATISFIED | `WalletService.list/summary`, test `WalletCrudIntegrationTest` |
| WALLET-02 | 02-02 | Tạo ví, tên không trùng, `current_balance=initial_balance` | ✓ SATISFIED | `WalletService.create`, test `createWallet_duplicateName_returns409WalletNameExists` |
| WALLET-03 | 02-02 | Sửa ví, không sửa `current_balance`/`type` | ✓ SATISFIED | `UpdateWalletRequest` thiếu field, `BALANCE_NOT_EDITABLE` |
| WALLET-04 | 02-02 | Xoá mềm, chặn ví cuối/ví còn giao dịch | ✓ SATISFIED | `WalletService.delete`, 2 test case |
| WALLET-05 | 02-02 | Sắp xếp lại ví | ✓ SATISFIED | `PATCH /wallets/reorder`, test `reorderWallets_returnsSortOrderMatchingArrayPosition` |
| WALLET-06 | 02-04 | Chuyển tiền 1 DB transaction, 1 bản ghi transfer, `fail_if_insufficient` | ✓ SATISFIED | `WalletTransferService.transfer`, 5 test case |
| WALLET-07 | 02-04 | Đối chiếu thủ công, đúng công thức, ghi log | ✓ SATISFIED | `WalletTransferService.reconcile`, không có `@Scheduled` (đúng D-25) |
| WALLET-08 | 02-04 | Điều chỉnh số dư sinh giao dịch bù, không ghi đè, chênh=0 không tạo | ✓ SATISFIED | `WalletTransferService.adjustBalance`, 7 test case đủ 2 chiều + chênh=0 + reconcile sau |
| CAT-01 | 02-03 | Xem cây 2 cấp, hệ thống + riêng, lọc `type` | ✓ SATISFIED | `CategoryService.list`, test `getCategoriesAsTree_...` |
| CAT-02 | 02-03 | Tạo cha/con tối đa 2 tầng, con cùng type, cha bắt buộc `category_group_id` | ✓ SATISFIED | `CategoryService.create`, 3 test case |
| CAT-03 | 02-03 | Sửa, không sửa `type`, đổi `parent_category_id` giữ 2 tầng | ✓ SATISFIED | `UpdateCategoryRequest` không có `type`, `CategoryService.update` |
| CAT-04 | 02-03 | Xoá, chặn còn con/giao dịch/ngân sách, hệ thống bất biến | ✓ SATISFIED | `CategoryService.delete`, `SYSTEM_CATEGORY_NOT_DELETABLE`/`CHILD_CATEGORIES_EXIST` |
| CAT-05 | 02-03 | Sắp xếp trong cùng cấp | ✓ SATISFIED | `PATCH /categories/reorder` |
| CAT-06 | 02-03 | Nhóm lớn (chỉ đọc) + kho icon lọc `icon_group`/`search` | ✓ SATISFIED | `GET /category-groups`, `GET /icons` |

**Ghi chú tài liệu:** Bảng theo dõi cuối `REQUIREMENTS.md` (dòng 214-227) vẫn ghi cột `Status` là "Pending" cho toàn bộ 14 requirement WALLET/CAT dù các mục `[x]` checkbox phía trên đã đánh dấu hoàn thành và code xác nhận đã triển khai đầy đủ. Đây là **doc drift ở bảng tổng hợp cuối file**, không phải thiếu sót chức năng — nên cập nhật cột Status thành "Done" cho 14 dòng này trong lần chỉnh sửa tài liệu tiếp theo, không chặn merge phase.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `CategoryService.java` | 106, 352 | `TODO Phase 4` (has_budget/CATEGORY_HAS_BUDGET) | ℹ️ Info | Chủ đích, đúng phạm vi D-28/plan — bảng `budgets` chưa có service ở Phase 2, không phải thiếu sót |
| `WalletService.java` | 246-253 | `reorder` bỏ qua kết quả `updateSortOrder` (silent failure nếu ID không thuộc quyền) | ⚠️ Warning | Đã ghi nhận ở `02-REVIEW.md` MD-02 — không chặn merge (không phải lỗ hổng bảo mật, chỉ là hành vi âm thầm sai khi client gửi ID sai), chưa được fix cùng đợt HG-01/HG-02/MD-01. Không nằm trong phạm vi 6 Success Criteria của phase, không tạo gap |
| `CategoryService.java` | 358-364 | `reorder` — cùng vấn đề MD-02 | ⚠️ Warning | Tương tự trên |

### Human Verification Required

Không có mục nào cần xác minh thủ công — toàn bộ 6 Success Criteria đều kiểm chứng được qua code + test tự động (Testcontainers PostgreSQL thật), không có hành vi UI/thời gian thực/dịch vụ ngoài cần con người xác nhận.

### Gaps Summary

Không có gap chặn merge. Toàn bộ 6 Success Criteria của ROADMAP.md Phase 2 đã được xác minh có bằng chứng code cụ thể, khớp `api/02-VI.md`/`api/03-DANH-MUC.md`/schema thật (`V1`, `V6`, `V8`). Hai finding High (`HG-01`, `HG-02`) và một Medium (`MD-01`) từ `02-REVIEW.md` đã được sửa và có test xác nhận (`02-REVIEW-FIX.md`, `mvn test` 77/77 PASS). Một finding Medium còn lại (`MD-02` — silent failure ở `reorder`) chưa sửa nhưng không ảnh hưởng tới goal của phase (không phải lỗ hổng quyền, không nằm trong 6 Success Criteria) — có thể để lại làm nợ kỹ thuật ghi nhận cho phase sau nếu cần.

Doc drift nhỏ ở bảng trạng thái cuối `REQUIREMENTS.md` (ghi "Pending" thay vì "Done") — khuyến nghị cập nhật nhưng không chặn.

---

_Verified: 2026-08-23T11:10:00Z_
_Verifier: Claude (gsd-verifier)_

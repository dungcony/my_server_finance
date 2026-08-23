---
phase: 02-vi-danh-muc
fixed_at: 2026-08-23T10:55:00Z
review_path: .planning/phases/02-vi-danh-muc/02-REVIEW.md
iteration: 1
findings_in_scope: 2
fixed: 2
skipped: 0
status: all_fixed
note: Ngoài phạm vi (2 finding High), fix bổ sung finding MD-01 (Medium) theo yêu cầu tường minh của người thực thi — xem mục "Fix bổ sung ngoài phạm vi" bên dưới.
---

# Phase 02: Ví & Danh mục — Báo cáo Fix Code Review

**Fixed at:** 2026-08-23T10:55:00Z
**Source review:** .planning/phases/02-vi-danh-muc/02-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings trong phạm vi (severity High): 2
- Đã sửa: 2
- Bỏ qua: 0
- Fix bổ sung ngoài phạm vi (theo yêu cầu tường minh, không thuộc High): 1 (MD-01)

## Fixed Issues

### HG-01: `CategoryService.create` không kiểm tra quyền sở hữu danh mục cha khi tạo con

**Files modified:** `src/main/java/com/datn/financeapp/category/service/CategoryService.java`,
`src/test/java/com/datn/financeapp/category/CategoryAccessControlIntegrationTest.java`
**Commit:** `7a9a372`
**Applied fix:** Đổi `categoryRepository.findById(req.parentCategoryId())` (không lọc quyền) sang
`categoryRepository.findByIdAndVisibleToUser(req.parentCategoryId(), userId)` trong `create()` —
chỉ chấp nhận danh mục cha là hệ thống (`user_id IS NULL`) hoặc đúng chủ sở hữu, trả `404 NOT_FOUND`
nếu không, không lộ việc UUID có tồn tại hay không.

Thêm test `userA_createChildUnderUserBPrivateParent_returns404NotFound` trong
`CategoryAccessControlIntegrationTest`: user A gọi `POST /categories` với `parent_category_id` trỏ
tới danh mục riêng của user B → xác nhận `404` + `error.code = NOT_FOUND`.

### HG-02: `CategoryService.update` không kiểm tra quyền sở hữu danh mục cha mới khi đổi `parent_category_id`

**Files modified:** `src/main/java/com/datn/financeapp/category/service/CategoryService.java`,
`src/test/java/com/datn/financeapp/category/CategoryAccessControlIntegrationTest.java`
**Commit:** `c4670a8`
**Applied fix:** Cùng cách sửa như HG-01, áp dụng cho nhánh `update()` khi client đổi
`parent_category_id` sang cha mới — đổi `findById` không lọc quyền sang `findByIdAndVisibleToUser`.

Thêm test `userA_patchOwnCategoryParentToUserBPrivateCategory_returns404NotFound`: user A `PATCH`
danh mục của chính mình, đổi `parent_category_id` sang danh mục riêng của user B → xác nhận `404`.

## Fix bổ sung ngoài phạm vi (theo yêu cầu tường minh)

### MD-01: `WalletTransferService.lockAndCheckOwnership` ném NPE thay vì 404 khi ví là ví chung

Không thuộc scope 2 finding High, nhưng người thực thi fix yêu cầu tường minh sửa cùng đợt (mô tả
lỗi khớp 1:1 với MD-01 trong `02-REVIEW.md`) — ghi nhận công khai để tài liệu không lệch code.

**Files modified:** `src/main/java/com/datn/financeapp/wallet/service/WalletTransferService.java`,
`src/test/java/com/datn/financeapp/wallet/WalletTransferIntegrationTest.java`
**Commit:** `e0fe729`
**Applied fix:** Đảo thứ tự so sánh trong `lockAndCheckOwnership` từ
`!wallet.getUserId().equals(userId)` (ném NPE khi `wallet.getUserId()` null — ví chung, xem
`ck_wallets_owner`) sang `!userId.equals(wallet.getUserId())` — `userId` luôn khác null (lấy từ
`SecurityContextUtil`) nên null-safe với mọi trường hợp ví chung, trả đúng `404 NOT_FOUND`.

Thêm test `transferFromGroupWalletWithNullUserId_returns404NotFound` trong
`WalletTransferIntegrationTest`: tạo trực tiếp một `group` + một ví chung (`user_id IS NULL`,
`group_id` trỏ tới group vừa tạo) qua `jdbcTemplate`, gọi `POST /wallets/transfer` với
`source_wallet_id` là ví chung đó → xác nhận `404` (không phải `500`).

## Kiểm tra sau fix

`mvn test` chạy toàn bộ: **77/77 test PASS** (74 test gốc + 3 test mới thêm ở trên), `BUILD SUCCESS`.

## Skipped Issues

Không có finding nào bị bỏ qua trong phạm vi (2 finding High).

---

_Fixed: 2026-08-23T10:55:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_

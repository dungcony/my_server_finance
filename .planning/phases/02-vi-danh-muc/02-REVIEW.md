---
phase: 02-vi-danh-muc
reviewed: 2026-08-23T10:41:46Z
depth: standard
files_reviewed: 30
files_reviewed_list:
  - src/main/java/com/datn/financeapp/auth/service/AuthService.java
  - src/main/java/com/datn/financeapp/wallet/entity/Wallet.java
  - src/main/java/com/datn/financeapp/wallet/repository/WalletRepository.java
  - src/main/java/com/datn/financeapp/wallet/service/WalletService.java
  - src/main/java/com/datn/financeapp/wallet/service/WalletTransferService.java
  - src/main/java/com/datn/financeapp/wallet/controller/WalletController.java
  - src/main/java/com/datn/financeapp/wallet/dto/AdjustBalanceRequest.java
  - src/main/java/com/datn/financeapp/wallet/dto/AdjustBalanceResponse.java
  - src/main/java/com/datn/financeapp/wallet/dto/CreateWalletRequest.java
  - src/main/java/com/datn/financeapp/wallet/dto/ReconcileResponse.java
  - src/main/java/com/datn/financeapp/wallet/dto/ReorderWalletsRequest.java
  - src/main/java/com/datn/financeapp/wallet/dto/TransferRequest.java
  - src/main/java/com/datn/financeapp/wallet/dto/TransferResponse.java
  - src/main/java/com/datn/financeapp/wallet/dto/UpdateWalletRequest.java
  - src/main/java/com/datn/financeapp/wallet/dto/WalletDetailResponse.java
  - src/main/java/com/datn/financeapp/wallet/dto/WalletResponse.java
  - src/main/java/com/datn/financeapp/wallet/dto/WalletStatsDto.java
  - src/main/java/com/datn/financeapp/wallet/dto/WalletSummaryResponse.java
  - src/main/java/com/datn/financeapp/category/entity/Category.java
  - src/main/java/com/datn/financeapp/category/entity/CategoryGroup.java
  - src/main/java/com/datn/financeapp/category/entity/Icon.java
  - src/main/java/com/datn/financeapp/category/repository/CategoryRepository.java
  - src/main/java/com/datn/financeapp/category/repository/CategoryGroupRepository.java
  - src/main/java/com/datn/financeapp/category/repository/IconRepository.java
  - src/main/java/com/datn/financeapp/category/service/CategoryService.java
  - src/main/java/com/datn/financeapp/category/controller/CategoryController.java
  - src/main/java/com/datn/financeapp/category/dto/CategoryDetailResponse.java
  - src/main/java/com/datn/financeapp/category/dto/CategoryGroupResponse.java
  - src/main/java/com/datn/financeapp/category/dto/CategoryResponse.java
  - src/main/java/com/datn/financeapp/category/dto/CreateCategoryRequest.java
  - src/main/java/com/datn/financeapp/category/dto/IconGroupResponse.java
  - src/main/java/com/datn/financeapp/category/dto/IconResponse.java
  - src/main/java/com/datn/financeapp/category/dto/ReorderCategoriesRequest.java
  - src/main/java/com/datn/financeapp/category/dto/UpdateCategoryRequest.java
  - src/test/java/com/datn/financeapp/wallet/WalletAccessControlIntegrationTest.java
  - src/test/java/com/datn/financeapp/wallet/WalletAdjustBalanceIntegrationTest.java
  - src/test/java/com/datn/financeapp/wallet/WalletCrudIntegrationTest.java
  - src/test/java/com/datn/financeapp/wallet/WalletTransferConcurrencyTest.java
  - src/test/java/com/datn/financeapp/wallet/WalletTransferIntegrationTest.java
  - src/test/java/com/datn/financeapp/category/CategoryAccessControlIntegrationTest.java
  - src/test/java/com/datn/financeapp/category/CategoryTreeIntegrationTest.java
findings:
  critical: 0
  high: 2
  medium: 2
  low: 3
  total: 7
status: issues_found
---

# Phase 02: Ví & Danh mục — Báo cáo Code Review

**Reviewed:** 2026-08-23T10:41:46Z
**Depth:** standard
**Files Reviewed:** 30 (main) + 7 (test)
**Status:** issues_found

## Tóm tắt

Đã đọc toàn bộ code của module Ví (`wallet/`) và Danh mục (`category/`) cùng thay đổi liên quan ở
`AuthService` (D-26), đối chiếu với `db/migration/V1`, `V6`, `V8` và `api/02-VI.md`,
`api/03-DANH-MUC.md`.

**Điểm làm đúng, đáng ghi nhận:**
- `group_members.is_active` (không phải `status`) được dùng nhất quán ở mọi câu truy vấn có điều
  kiện quyền nhóm (`WalletRepository`, `WalletService.summary`, `AuthService.getMe`) — đúng schema
  thật, không lặp lại lỗi mà `02-CONTEXT.md` D-27 mô tả (context ghi nhầm `status = 'active'` nhưng
  code triển khai đúng theo schema).
- Cập nhật `current_balance` luôn qua `UPDATE ... SET current_balance = current_balance + :delta`
  (atomic), không load-modify-save — `WalletRepository.adjustBalance`.
- Chuyển tiền khoá 2 ví theo `UUID.compareTo()` cố định trước khi lock, đúng thứ tự chống deadlock.
- `amount`/`current_balance`/`initial_balance` đều `Long`, không có `Double`/`float` ở bất kỳ đâu.
- Quyền 404-không-403 được tuân thủ nhất quán ở các đường tra cứu chính
  (`WalletRepository.findByIdForUser`, `CategoryRepository.findByIdAndVisibleToUser`).
- WALLET-08 (điều chỉnh số dư) đúng nghiệp vụ: không ghi đè `current_balance`, sinh giao dịch bù,
  `difference == 0` không tạo bản ghi — test `WalletAdjustBalanceIntegrationTest` phủ đủ 2 chiều +
  đối chiếu sau điều chỉnh.

**Vấn đề chính cần sửa trước khi merge:** hai lỗ hổng quyền ở `CategoryService` (tạo/sửa danh mục
con dưới cha KHÔNG kiểm tra chủ sở hữu) và một lỗi crash tiềm ẩn (NPE) ở
`WalletTransferService.lockAndCheckOwnership` khi ví là ví chung (`user_id IS NULL`).

## Vấn đề mức High

### HG-01: `CategoryService.create` không kiểm tra quyền sở hữu danh mục cha khi tạo con

**File:** `src/main/java/com/datn/financeapp/category/service/CategoryService.java:122-127`
**Vấn đề:** Khi tạo danh mục con (`parentCategoryId` khác null), code tra cha bằng
`categoryRepository.findById(req.parentCategoryId())` — **không** lọc quyền sở hữu. Repository đã
có sẵn `findByIdAndVisibleToUser` đúng mục đích này (dùng ở `delete()` cho
`replacementCategoryId`), nhưng `create()` không dùng.

Hệ quả: User A đoán/biết được UUID danh mục **riêng** (không phải hệ thống) của User B, có thể gọi
`POST /categories` với `parent_category_id` đó và tạo thành công một danh mục con gắn dưới cây của
B. Vi phạm nguyên tắc riêng tư mặc định (CLAUDE.md gốc mục 3) — dữ liệu cá nhân (ở đây là cấu trúc
cây danh mục) bị một user khác thao túng dù không "đọc" được nội dung, và về lâu dài làm lộ việc
UUID đó tồn tại và là danh mục cha hợp lệ (oracle timing/behavior).

**Cách khắc phục:**
```java
if (req.parentCategoryId() != null) {
    parent = categoryRepository
            .findByIdAndVisibleToUser(req.parentCategoryId(), userId)
            .orElseThrow(() -> new BusinessException(
                    "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy danh mục cha."));
    ...
```
Đồng thời cân nhắc chặn cả trường hợp cha là danh mục hệ thống nếu nghiệp vụ không cho phép user
tạo con dưới danh mục hệ thống của người khác định nghĩa — nhưng theo `api/03-DANH-MUC.md` mục 9,
danh mục hệ thống dùng chung nên cha hệ thống vẫn hợp lệ, chỉ cha **riêng của user khác** mới cần
chặn. `findByIdAndVisibleToUser` xử lý đúng cả hai trường hợp (hệ thống OR đúng chủ).

---

### HG-02: `CategoryService.update` không kiểm tra quyền sở hữu danh mục cha mới khi đổi `parent_category_id`

**File:** `src/main/java/com/datn/financeapp/category/service/CategoryService.java:234-239`
**Vấn đề:** Cùng lỗi như HG-01 nhưng ở luồng sửa: khi đổi `parent_category_id` sang một danh mục
cha khác, code tra `newParent` bằng `categoryRepository.findById(newParentId)` — không lọc quyền.
User A có thể chuyển danh mục con của chính mình thành con của một danh mục **riêng của User B**.

**Cách khắc phục:**
```java
Category newParent = categoryRepository
        .findByIdAndVisibleToUser(newParentId, userId)
        .orElseThrow(() -> new BusinessException(
                "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy danh mục cha."));
```

---

## Vấn đề mức Medium

### MD-01: `WalletTransferService.lockAndCheckOwnership` ném `NullPointerException` thay vì 404 khi ví là ví chung

**File:** `src/main/java/com/datn/financeapp/wallet/service/WalletTransferService.java:213`
**Vấn đề:**
```java
if (!wallet.getUserId().equals(userId)) {
```
`wallets.user_id` có thể `NULL` khi ví thuộc nhóm (`ck_wallets_owner` — `db/migration/V1__nen_tang.sql`
dòng 108-111, xác nhận qua `api/02-VI.md` mục 8 cho phép chuyển tiền tới/từ ví chung). Nếu
`walletId` trong `transfer`/`adjustBalance` trỏ tới một ví chung (`group_id IS NOT NULL`,
`user_id IS NULL`), `wallet.getUserId()` trả `null` và dòng này ném `NullPointerException` — lộ ra
thành lỗi 500 không kiểm soát thay vì `NOT_FOUND` (404) như thiết kế quyền D-27 yêu cầu. Đây đúng
là loại lỗi "crash do null pointer" nằm trong phạm vi review (mục Bugs).

Phase 2 tự thân chưa có đường tạo ví chung qua API (`WalletService.create` chặn `group_id` bằng
`NOT_GROUP_MEMBER` — dòng 125-133), nên lỗi chưa lộ qua HTTP ở phase này. Nhưng do dữ liệu ví chung
có thể tồn tại từ nguồn khác (seed thủ công, migration test, hoặc Phase 5 bật tính năng mà tái sử
dụng nguyên `WalletTransferService`), nên đây là quả bom hẹn giờ — không phải lỗi lý thuyết suông.

**Cách khắc phục:**
```java
private Wallet lockAndCheckOwnership(UUID walletId, UUID userId) {
    Wallet wallet = walletRepository
            .findByIdForUpdate(walletId)
            .orElseThrow(() -> new BusinessException(
                    "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy ví."));
    if (!userId.equals(wallet.getUserId())) {
        throw new BusinessException("NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Không tìm thấy ví.");
    }
    return wallet;
}
```
Đảo thứ tự so sánh (`userId.equals(wallet.getUserId())`, `userId` chắc chắn không null vì lấy từ
`SecurityContextUtil`) để null-safe. Ghi chú Javadoc hiện tại của method (dòng 204-207) đã nói "Phase
2 chỉ có nhánh cá nhân" nhưng code không thực sự chỉ xử lý nhánh đó một cách an toàn — nên sửa luôn
thay vì chỉ dựa vào comment.

---

### MD-02: `WalletService.reorder` và `CategoryService.reorder` bỏ qua ID không thuộc quyền mà không báo lỗi

**File:** `src/main/java/com/datn/financeapp/wallet/service/WalletService.java:247-253`,
`src/main/java/com/datn/financeapp/category/service/CategoryService.java:360-366`
**Vấn đề:** Cả hai vòng lặp gọi `updateSortOrder(id, i, userId, ...)` — method này trả về số dòng
bị ảnh hưởng (`int`) nhưng kết quả bị bỏ qua hoàn toàn. Nếu client gửi một ID không tồn tại, đã bị
xoá mềm, hoặc thuộc về user khác, câu UPDATE âm thầm ảnh hưởng 0 dòng — không có lỗi nào được báo,
`sort_order` của các phần tử còn lại trong mảng vẫn được ghi bình thường. Người dùng không biết một
phần yêu cầu sắp xếp của họ đã thất bại thầm lặng.

Đây không phải lỗ hổng bảo mật (không ghi được dữ liệu của người khác — điều kiện `userId` trong
`WHERE` vẫn đúng), nhưng là hành vi âm thầm sai (silent failure) — thuộc nhóm "Bugs / unhandled edge
case" theo tiêu chí review.

**Cách khắc phục:** Cộng dồn kết quả trả về của `updateSortOrder` và ném lỗi nếu tổng số dòng cập
nhật ít hơn `ids.size()`:
```java
int totalUpdated = 0;
for (int i = 0; i < ids.size(); i++) {
    totalUpdated += walletRepository.updateSortOrder(ids.get(i), i, userId);
}
if (totalUpdated != ids.size()) {
    throw new BusinessException("NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Một số ví không tồn tại hoặc không thuộc quyền.");
}
```
Áp dụng tương tự cho `CategoryService.reorder`.

---

## Vấn đề mức Low

### TH-01: `AdjustBalanceRequest.actualBalance` không có ràng buộc `@PositiveOrZero` ở tầng DTO

**File:** `src/main/java/com/datn/financeapp/wallet/dto/AdjustBalanceRequest.java:9`
**Vấn đề:** Ràng buộc "số dư thực tế không được âm" chỉ được kiểm tra thủ công trong Service
(`WalletTransferService.adjustBalance` dòng 114-117) thay vì khai báo ngay ở tầng validation
(`@PositiveOrZero`). Không sai về mặt chức năng (test `negativeActualBalance_returns400InvalidAmount`
xác nhận hoạt động đúng), nhưng để hai lớp validate rải rác — annotation cho các ràng buộc định
dạng, Service cho ràng buộc nghiệp vụ có mã lỗi riêng (`INVALID_AMOUNT`) — là chủ đích hợp lý ở đây
vì cần trả đúng mã lỗi nghiệp vụ thay vì `VALIDATION_FAILED` chung chung của Bean Validation. Không
bắt buộc sửa, ghi nhận để nhất quán khi review các DTO khác.

---

### TH-02: `WalletService.detail`/`CategoryService.detail` gọi nhiều truy vấn `jdbcTemplate.queryForObject` riêng lẻ cho từng con số thống kê

**File:** `src/main/java/com/datn/financeapp/wallet/service/WalletService.java:255-277`,
`src/main/java/com/datn/financeapp/category/service/CategoryService.java:98-104`
**Vấn đề:** 3-4 round-trip DB riêng cho mỗi lần gọi `GET /wallets/{id}` hoặc
`GET /categories/{id}` thay vì gộp vào một câu SELECT. Đây là vấn đề hiệu năng (ngoài phạm vi v1
theo review scope) — chỉ ghi chú, không tính là finding chặn merge.

---

### TH-03: `WalletService.create` không kiểm tra `req.icon()` có tồn tại/hợp lệ

**File:** `src/main/java/com/datn/financeapp/wallet/service/WalletService.java:147`
**Vấn đề:** Không phải lỗi — `icon` của ví là chuỗi tự do theo `api/02-VI.md` mục 4 (khác với
`icon_id` FK bắt buộc của danh mục), nên không cần validate tồn tại. Ghi chú tại đây chỉ để xác nhận
đã đối chiếu và không phải bug, tránh reviewer sau nhầm lẫn với `CATEGORY_ID` validation của
`CategoryService.create`.

---

## Đối chiếu quy tắc bất biến (review_focus)

| # | Quy tắc | Kết quả |
|---|---|---|
| 1 | `amount`/số tiền là số nguyên, không dùng float/double | Đạt — toàn bộ `Long` |
| 2 | `amount` luôn dương, chiều tiền suy từ `type` | Đạt — `WalletTransferService.adjustBalance` dùng `Math.abs(difference)` + suy `type` từ dấu, transfer dùng `@Positive Long amount` |
| 3 | Cập nhật `current_balance` atomic, trong transaction | Đạt — `WalletRepository.adjustBalance` (JPQL UPDATE), mọi service method có `@Transactional` |
| 4 | Chuyển tiền khoá 2 ví thứ tự cố định, kiểm tra quyền SAU khi lock | Đạt cho ví cá nhân; **NPE khi ví chung** — xem MD-01 |
| 5 | Quyền trong SQL, không lọc ở code; không có quyền → 404 | Đạt phần lớn (Wallet, Category detail/delete); **2 chỗ ở CategoryService.create/update bỏ sót** — xem HG-01, HG-02 |
| 6 | `group_members.is_active = true` (không phải `status`) | Đạt — dùng đúng ở mọi nơi có |
| 7 | Không viết trigger CSDL cập nhật số dư ví | Đạt — không có migration mới trong scope này |
| 8 | Response `{success,data}`/`{success,error}`; POST hỗ trợ Idempotency-Key | Đạt — `ApiResponse.of`, `@Idempotent` gắn đúng trên `POST /wallets`, `POST /wallets/transfer`, `POST /wallets/{id}/adjust-balance`, `POST /categories` theo D-11 |
| 9 | Đối chiếu với `db/migration/V*.sql` | Đạt — cột `bpchar(7)`, `counts_in_report`, `source='adjustment'`, `fn_category_tree` đều khớp V1/V6/V8; không phát hiện chỗ nào giả định cột/kiểu không tồn tại |

---

_Reviewed: 2026-08-23T10:41:46Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_

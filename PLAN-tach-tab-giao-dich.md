# Kế hoạch tách Tab Giao Dịch & phân quyền hiển thị theo vai trò

> Trạng thái: **CHỜ DUYỆT** — chưa sửa code. Bấm "process" thì mới bắt đầu.

## 0. Quy tắc nghiệp vụ đã chốt

| Hành động                            | OWNER         | THỦ QUỸ               | MEMBER                                                                          |
|--------------------------------------|---------------|-----------------------|---------------------------------------------------------------------------------|
| Tạo giao dịch                        | ✅            | ✅                    | ✅                                                                              |
| Xem danh sách chung                  | ✅            | ✅                    | ✅                                                                              |
| Xem bảng chờ duyệt / duyệt / từ chối | ✅            | ✅                    | ❌                                                                              |
| Sửa giao dịch                        | Mọi giao dịch | Mọi giao dịch         | Chỉ giao dịch **mình tạo** (`created_by == tôi`), loại `EXPENSE`/`CONTRIBUTION` |
| Xóa giao dịch                        | Mọi giao dịch | ❌ **Không được xóa** | Chỉ giao dịch **mình tạo** và đang **PENDING**                                  |

Ghi chú:

- Quyền sửa/xóa tính theo **người tạo** (`created_by`), không theo người chịu (`transactor_id`). Owner/thủ quỹ tạo hộ
  cho thành viên thì chỉ hai người đó được sửa, dù thành viên là người chịu.
- Thủ quỹ không có quyền xóa **kể cả** giao dịch PENDING do chính họ tạo (thủ quỹ là MEMBER kèm cờ `isTreasurer`,
  validator phải kiểm tra thủ quỹ **trước** nhánh "thành viên xóa PENDING của mình").
- Ẩn nút trên UI chỉ để giao diện gọn. Quyền thật do backend chặn.

## 1. Các file sẽ thay đổi

Backend (mới so với bản plan trước, vì quyền xóa của MEMBER hiện chưa có):

- `src/main/java/com/datn/financeapp/group/validator/GroupPermissionValidator.java` — thêm
  `verifyTransactionDeletePermission`.
- `src/main/java/com/datn/financeapp/group/service/impl/GTransactionServiceImpl.java` — `delete()` gọi validator mới
  thay cho `if (!isOwner) throw`.
- `src/main/java/com/datn/financeapp/common/exception/ErrorCode.java` — thêm mã lỗi mới `FORBIDDEN_TRANSACTION_DELETE(HttpStatus.FORBIDDEN, "Bạn không có quyền xóa giao dịch này.")`.
- `src/test/java/com/datn/financeapp/group/service/impl/GTransactionServiceImplTest.java` — thêm test quyền xóa.
- `api/04-*.md` — cập nhật mô tả quyền xóa giao dịch nhóm (đối chiếu trước khi sửa, theo CLAUDE.md gốc).

Giao diện test:

- `src/main/resources/static/pages/group-test.html`
- `src/main/resources/static/js/group-test.js`

## 2. Lý do thay đổi

- Nút "Chọn" nhóm đang gọi `detailGroup()` → `loadPendingTransactions()` → `/transactions/pending` chỉ dành cho người
  duyệt, MEMBER bị 403.
- Chức năng giao dịch đang lẫn vào `tab-fund`, khó theo dõi theo vai trò.
- Backend `delete` hiện chỉ OWNER → không đáp ứng "MEMBER xóa được giao dịch PENDING của mình".

## 3. Hướng giải quyết

### 3.1 Backend — quyền xóa

`verifyTransactionDeletePermission(txn, operatorId, authInfo)`:

```java
// chủ nhóm xóa được mọi giao dịch
if (authInfo.isOwner()) return;

// thủ quỹ không có quyền xóa
if (authInfo.isTreasurer())
    throw new BusinessException(ErrorCode.FORBIDDEN_TRANSACTION_DELETE);

// thành viên thường chỉ xóa giao dịch do mình tạo
if (!txn.getCreatedBy().equals(operatorId))
    throw new BusinessException(ErrorCode.FORBIDDEN_TRANSACTION_DELETE);

// và chỉ khi giao dịch còn chờ duyệt
if (txn.getStatus() != TransactionStatus.PENDING)
    throw new BusinessException(ErrorCode.FORBIDDEN_TRANSACTION_DELETE);
```

`delete()` trong service: lấy `authInfo`, `findActiveTransaction`, gọi validator, giữ nguyên phần hoàn tác quỹ + xóa
mềm. Thứ tự đổi: tìm giao dịch **trước** rồi mới kiểm tra quyền (cần `createdBy`, `status`). Không lộ tồn tại giao dịch
vì `findActiveTransaction` đã lọc theo `groupId`, và người ngoài nhóm bị chặn từ `getAuthInfo`.

Test mới (tiếng Việt ở `@DisplayName`, tên method tiếng Anh):

- MEMBER xóa PENDING do mình tạo → xóa mềm thành công.
- MEMBER xóa CONFIRMED do mình tạo → `FORBIDDEN_TRANSACTION_DELETE`.
- MEMBER xóa PENDING do người khác tạo → bị chặn.
- THỦ QUỸ xóa PENDING (kể cả do chính họ tạo) → bị chặn.
- OWNER xóa CONFIRMED → vẫn được, quỹ được hoàn tác.
- Test cũ `delete_Contribution_SoftDeletes` giữ nguyên, phải xanh.

### 3.2 Giao diện — thanh điều hướng

1. `🏢 Quản Lý Nhóm`
2. `👥 Thành Viên`
3. `💳 Giao Dịch` *(mới)*
4. `💰 Quỹ Nhóm`
5. `📊 Báo Cáo Tài Chính`

### 3.3 Giao diện — nội dung `#tab-transactions`

- **Khối 1 — Tạo giao dịch** (`POST /transactions`): mọi thành viên.
- **Khối 2 — Chờ duyệt** (`GET /transactions/pending`): chỉ OWNER + THỦ QUỸ. **Di chuyển** (không copy) từ `tab-fund`
  sang đây. Gồm Duyệt / Từ chối / Duyệt tất cả / Từ chối tất cả.
- **Khối 3 — Giao dịch của tôi** (`GET /transactions?transactor_id={tôi}`): các giao dịch tôi là người chịu. Cột: mã,
  loại, số tiền, trạng thái, **người tạo**.
    - Nút **Cập nhật** hiện khi: `created_by == tôi` **và** loại là `EXPENSE`/`CONTRIBUTION`. OWNER, THỦ QUỸ thấy nút ở
      mọi dòng.
    - Nút **Xóa** hiện khi: OWNER (mọi dòng), hoặc MEMBER thường với `created_by == tôi` **và** `status == PENDING`. THỦ
      QUỸ không thấy nút Xóa.
    - Cập nhật qua form/modal gọi `PUT /transactions/{txnId}`.
- **Khối 4 — Danh sách chung** (`GET /transactions`): mọi thành viên, có bộ lọc nguồn tiền, loại, trạng thái.

### 3.4 Giao diện — phân quyền hiển thị

Tính một lần trong `detailGroup()` rồi lưu vào biến trạng thái (reset khi đổi nhóm):

- `isOwner = (group.my_role === 'OWNER')`
- `isTreasurer`: tìm `myMember` trong `group.members[]` bằng `user.id` (đã lưu ở `group-test.js:47`), đọc
  `is_treasurer`. Xử lý trường hợp `members` rỗng hoặc không thấy → coi như `false`.

Áp dụng class `role-owner-only` / `role-reviewer-only` (OWNER hoặc thủ quỹ):

| Vị trí                                                                                   | Điều kiện hiện                                                                   |
|------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------|
| Tab Quản lý nhóm: Xem chi tiết?, Lưu trữ, Hủy lưu trữ, Xóa nhóm, Cập nhật nhóm (PATCH)   | OWNER                                                                            |
| Tab Thành viên: Thêm thành viên, Chuyển Owner, Duyệt/Từ chối chờ, Kick, bộ lọc `PENDING` | OWNER                                                                            |
| Tab Quỹ: Đổi thủ quỹ                                                                     | OWNER                                                                            |
| Tab Quỹ: Kiểm kê / Đối soát                                                              | OWNER hoặc THỦ QUỸ                                                               |
| Tab Giao dịch: Khối 2 (chờ duyệt)                                                        | OWNER hoặc THỦ QUỸ                                                               |
| Badge "giao dịch chờ duyệt"                                                              | Bấm → `switchTab('tab-transactions')` (đổi từ `tab-fund`, `group-test.html:324`) |

Nút "Chọn" nhóm: chỉ `setCurrentGroupId`, gọi `detailGroup()` và `loadPendingCount()`.

- `detailGroup()` **bỏ** lời gọi `loadPendingTransactions()` ở `group-test.js:392`.
- `loadPendingTransactions()` chỉ chạy khi là OWNER/THỦ QUỸ **và** mở tab Giao dịch (hoặc bấm "Tải lại").
- `loadPendingCount()` an toàn với MEMBER: backend trả về 0 chứ không báo lỗi (`GroupServiceImpl.pendingCount`), không
  cần chặn.

## 4. Kịch bản kiểm thử

**MEMBER:**

1. Bấm "Chọn" nhóm → không còn 403; các nút quản trị ẩn.
2. Tab Giao dịch: không thấy bảng chờ duyệt; thấy "Giao dịch của tôi".
3. Giao dịch PENDING do mình tạo: có nút Cập nhật + Xóa → xóa thành công.
4. Giao dịch CONFIRMED do mình tạo: có Cập nhật, **không** có Xóa; gọi API xóa trực tiếp → bị chặn.
5. Giao dịch owner/thủ quỹ tạo hộ mình: hiện dòng nhưng **không** có nút; gọi API sửa trực tiếp → 403.
6. Giao dịch loại khác `EXPENSE`/`CONTRIBUTION`: không có nút Cập nhật.

**OWNER:** thấy đủ nút quản trị, duyệt thành viên, đổi thủ quỹ, duyệt giao dịch; sửa/xóa được mọi giao dịch.

**THỦ QUỸ:** thấy bảng chờ duyệt + kiểm kê; sửa được mọi giao dịch; **không** thấy nút Xóa; không thấy nút Owner (chuyển
owner, kick, đổi thủ quỹ).

Test tự động: chỉ chạy `GTransactionServiceImplTest` (theo `test-rule.md`, không chạy full).

## 5. Câu hỏi còn mở

Không còn. Đã chốt: MEMBER xóa được giao dịch PENDING của mình; sửa/xóa theo `created_by`; thủ quỹ không xóa.

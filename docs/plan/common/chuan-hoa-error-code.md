# Plan: Chuẩn hóa naming convention cho ErrorCode

- **Phạm vi:** `common/exception/ErrorCode.java` + 65 file tham chiếu (46 main + 19 test)
- **Trạng thái:** ⏳ CHỜ XÁC NHẬN
- **Xác nhận:** `[ ] PROCESS`

---

## 0. Bối cảnh

`ErrorCode.java` hiện có **103 mã lỗi**, đặt tên không theo convention thống nhất:
- Có mã đã có prefix module (`WALLET_NAME_EXISTS`, `GROUP_NOT_FOUND`) — đọc biết ngay thuộc module nào
- Có mã không prefix (`TRANSACTION_AMOUNT_INVALID`, `AUTH_RESET_CODE_INVALID`, `TRANSACTION_CATEGORY_REQUIRED`) — phải mở file mới biết dùng ở đâu
- Có mã sai chính tả / tối nghĩa (`GROUP_MEMBER_NOT_IN_GROUP`, `GROUP_MINIMUM_MEMBERS_REQUIRED`)
- Có mã trùng nghĩa (`GROUP_PENDING_TXN_EXIST` vs `GROUP_PENDING_TXN_EXIST`)

**Đây là breaking change API** — `getCode()` trả `name()`, tên enum chính là mã lỗi đi ra JSON.
Flutter app (`lib/core/network/api_error.dart`) đang bắt theo tên cũ.

---

## 1. Lợi ích

| # | Lợi ích | Giải thích |
|---|---------|------------|
| 1 | **Dự đoán được tên** | Dev mới không cần mở file — biết module là đoán được prefix, giảm thời gian tra cứu |
| 2 | **Grep/tìm kiếm nhanh** | `grep AUTH_` ra hết lỗi auth, `grep GROUP_TXN_` ra hết lỗi giao dịch nhóm — hiện tại phải biết trước tên mới grep được |
| 3 | **Tránh xung đột tên** | `TRANSACTION_CATEGORY_REQUIRED` hiện dùng cho giao dịch cá nhân, nhưng nhóm cũng có `GROUP_TXN_CATEGORY_REQUIRED`. Thêm prefix phân biệt rõ: `TRANSACTION_CATEGORY_REQUIRED` vs `GROUP_TXN_CATEGORY_REQUIRED` |
| 4 | **Sửa lỗi thật** | 2 typo, 2 câu tối nghĩa, 1 trùng lặp, 1 message sai chính tả — lần này sửa luôn |
| 5 | **Convention cho tương lai** | Phase 5 backend (nhóm gia đình + AI) sẽ thêm nhiều mã mới — có convention thì không lặp lại tình trạng hiện tại |

## 2. Rủi ro & chi phí

| # | Rủi ro | Mức độ | Giảm thiểu |
|---|--------|--------|------------|
| 1 | **Breaking change API** — Flutter app phải sửa đồng thời | **Cao** | Sửa backend xong → liệt kê bảng mapping cho Flutter, sửa trong cùng sprint |
| 2 | **Sót chỗ tham chiếu** — grep không bắt hết (ví dụ chuỗi trong test, tài liệu) | Trung bình | Dùng IDE rename (có compile check) + chạy `mvn test` sau khi đổi. Enum nên fail biên dịch nếu sót |
| 3 | **Tài liệu `api/*.md` lệch** — nếu quên cập nhật | Trung bình | Grep `api/` tìm mã cũ, sửa trong cùng commit |
| 4 | **Merge conflict** — nhánh khác đang dùng tên cũ | Thấp | Nhánh `group` là nhánh chính đang làm, không có nhánh song song |
| 5 | **103 mã đổi cùng lúc — review khó** | Trung bình | Phần lớn là thêm prefix cơ học (find-replace), logic không đổi. Bảng mapping bên dưới là checklist |

**Tổng chi phí ước tính:**
- Backend: ~65 file, phần lớn là find-replace enum name, không đổi logic
- Flutter: cần sửa `api_error.dart` + các chỗ xử lý mã lỗi cụ thể
- Tài liệu: grep `api/*.md` thay tên

---

## 3. Convention

```
[MODULE]_[CHỦ_THỂ]_[TRẠNG_THÁI]
```

**Quy tắc:**

1. **Mã chung** (dùng được ở mọi module): **không prefix** — `NOT_FOUND`, `VALIDATION_ERROR`, `FORBIDDEN`...
2. **Mã riêng module**: bắt buộc prefix theo bảng dưới
3. **Trạng thái đặt cuối**: `INVALID`, `REQUIRED`, `EXCEEDED`, `NOT_FOUND`, `FORBIDDEN`, `ALREADY_EXISTS`...
4. **Giao dịch nhóm dùng `GROUP_TXN_`** thay vì `GROUP_TRANSACTION_` để tránh tên quá dài
5. **Mã đã có prefix đúng**: giữ nguyên, không đổi lại

**Bảng prefix:**

| Module | Prefix |
|--------|--------|
| Xác thực & tài khoản | `AUTH_` |
| Ví | `WALLET_` |
| Danh mục | `CATEGORY_` |
| Giao dịch cá nhân | `TRANSACTION_` |
| Ngân sách | `BUDGET_` |
| Xuất tệp | `EXPORT_` |
| Sổ nợ | `DEBT_` |
| Định kỳ | `RECURRING_` |
| Mục tiêu | `GOAL_` |
| Nhóm (chung) | `GROUP_` |
| Giao dịch nhóm | `GROUP_TXN_` |

---

## 4. Bảng mapping đầy đủ

### 4.0 Mã chung — GIỮ NGUYÊN (12 mã)

| Tên hiện tại | Ghi chú |
|---|---|
| `VALIDATION_ERROR` | Chuẩn REST chung |
| `CONCURRENT_MODIFICATION` | Chuẩn REST chung |
| `UNAUTHENTICATED` | Chuẩn REST chung |
| `TOKEN_EXPIRED` | Chuẩn REST chung |
| `TOKEN_INVALID` | Chuẩn REST chung |
| `FORBIDDEN` | Chuẩn REST chung |
| `NOT_FOUND` | Chuẩn REST chung — dùng chung cho 52 chỗ, cố ý không tách theo module |
| `DUPLICATE` | Chuẩn REST chung |
| `RATE_LIMIT_EXCEEDED` | Chuẩn REST chung |
| `METHOD_NOT_ALLOWED` | Chuẩn REST chung |
| `INTERNAL_ERROR` | Chuẩn REST chung |
| `REQUEST_IN_PROGRESS` | Chuẩn REST chung |

### 4.1 Xác thực & tài khoản (15 mã đổi)

| Tên hiện tại | Tên mới | Lý do |
|---|---|---|
| `AUTH_EMAIL_ALREADY_EXISTS` | `AUTH_EMAIL_ALREADY_EXISTS` | Thêm prefix |
| `AUTH_CREDENTIALS_INVALID` | `AUTH_CREDENTIALS_INVALID` | Thêm prefix + trạng thái về cuối |
| `AUTH_ACCOUNT_LOCKED` | `AUTH_ACCOUNT_LOCKED` | Thêm prefix |
| `AUTH_ACCOUNT_NOT_VERIFIED` | `AUTH_ACCOUNT_NOT_VERIFIED` | Thêm prefix |
| `AUTH_ACCOUNT_BLOCKED` | `AUTH_ACCOUNT_BLOCKED` | Thêm prefix |
| `AUTH_REFRESH_TOKEN_INVALID` | `AUTH_REFRESH_TOKEN_INVALID` | Thêm prefix |
| `AUTH_RESET_CODE_INVALID` | `AUTH_RESET_CODE_INVALID` | Thêm prefix + rõ nghĩa "mã reset" |
| `AUTH_VERIFICATION_CODE_INVALID` | `AUTH_VERIFICATION_CODE_INVALID` | Thêm prefix |
| `AUTH_ACCOUNT_ALREADY_VERIFIED` | `AUTH_ACCOUNT_ALREADY_VERIFIED` | Thêm prefix |
| `AUTH_OLD_PASSWORD_INCORRECT` | `AUTH_OLD_PASSWORD_INCORRECT` | Thêm prefix + chuẩn hóa INCORRECT |
| `AUTH_PASSWORD_SAME_AS_OLD` | `AUTH_PASSWORD_SAME_AS_OLD` | Thêm prefix + rút gọn |
| `AUTH_PASSWORD_INCORRECT` | `AUTH_PASSWORD_INCORRECT` | Thêm prefix + chuẩn hóa INCORRECT |
| `AUTH_PASSWORD_NOT_SET` | `AUTH_PASSWORD_NOT_SET` | Thêm prefix |
| `AUTH_PASSWORD_ALREADY_SET` | `AUTH_PASSWORD_ALREADY_SET` | Thêm prefix |
| `AUTH_GOOGLE_TOKEN_INVALID` | `AUTH_GOOGLE_TOKEN_INVALID` | Thêm prefix + trạng thái về cuối |

### 4.2 Ví (5 mã đổi, 2 giữ nguyên)

| Tên hiện tại | Tên mới | Lý do |
|---|---|---|
| `WALLET_NAME_EXISTS` | *(giữ nguyên)* | Đã có prefix đúng |
| `WALLET_LAST_NOT_DELETABLE` | `WALLET_LAST_NOT_DELETABLE` | Thêm prefix + bỏ câu lệnh `CANNOT_` |
| `WALLET_HAS_TRANSACTIONS` | *(giữ nguyên)* | Đã có prefix đúng |
| `WALLET_BALANCE_NOT_EDITABLE` | `WALLET_BALANCE_NOT_EDITABLE` | Thêm prefix |
| `WALLET_TRANSFER_SAME_DESTINATION` | `WALLET_TRANSFER_SAME_DESTINATION` | Thêm prefix + rõ ngữ cảnh chuyển tiền |
| `WALLET_BALANCE_INSUFFICIENT` | `WALLET_BALANCE_INSUFFICIENT` | Thêm prefix + trạng thái về cuối |
| `GROUP_MEMBER_REQUIRED` | `GROUP_MEMBER_REQUIRED` | Chuyển về module GROUP cho đúng nghĩa |

### 4.3 Danh mục (4 mã đổi, 8 giữ nguyên)

| Tên hiện tại | Tên mới | Lý do |
|---|---|---|
| `CATEGORY_NAME_EXISTS` | *(giữ nguyên)* | Đã có prefix |
| `CATEGORY_DEPTH_EXCEEDED` | `CATEGORY_DEPTH_EXCEEDED` | Thêm prefix |
| `CATEGORY_GROUP_REQUIRED` | *(giữ nguyên)* | Đã có prefix |
| `CATEGORY_HAS_CHILDREN` | *(giữ nguyên)* | Đã có prefix |
| `CATEGORY_CHILDREN_EXIST` | `CATEGORY_CHILDREN_EXIST` | Thêm prefix + chuẩn hóa |
| `CATEGORY_HAS_TRANSACTIONS` | *(giữ nguyên)* | Đã có prefix |
| `CATEGORY_NOT_FOUND` | *(giữ nguyên)* | Đã có prefix |
| `CATEGORY_TYPE_MISMATCH_PARENT` | `CATEGORY_TYPE_MISMATCH_PARENT` | Thêm prefix |
| `CATEGORY_TYPE_NOT_EDITABLE` | `CATEGORY_TYPE_NOT_EDITABLE` | Thêm prefix |
| `SYSTEM_CATEGORY_NOT_EDITABLE` | *(giữ nguyên)* | Đã có prefix |
| `SYSTEM_CATEGORY_NOT_DELETABLE` | *(giữ nguyên)* | Đã có prefix |
| `CATEGORY_ICON_INVALID` | `CATEGORY_ICON_INVALID` | Thêm prefix + trạng thái về cuối |
| `SYSTEM_CATEGORY_MISSING` | *(giữ nguyên)* | Đã có prefix |

### 4.4 Giao dịch cá nhân (7 mã đổi, 1 giữ nguyên)

| Tên hiện tại | Tên mới | Lý do |
|---|---|---|
| `TRANSACTION_AMOUNT_INVALID` | `TRANSACTION_AMOUNT_INVALID` | Thêm prefix + trạng thái về cuối |
| `TRANSACTION_CATEGORY_REQUIRED` | `TRANSACTION_CATEGORY_REQUIRED` | Thêm prefix — phân biệt với `GROUP_TXN_CATEGORY_REQUIRED` |
| `TRANSACTION_CATEGORY_NOT_ALLOWED` | `TRANSACTION_CATEGORY_NOT_ALLOWED` | Thêm prefix |
| `TRANSACTION_CATEGORY_TYPE_MISMATCH` | `TRANSACTION_CATEGORY_TYPE_MISMATCH` | Thêm prefix |
| `TRANSACTION_DEST_WALLET_REQUIRED` | `TRANSACTION_DEST_WALLET_REQUIRED` | Thêm prefix |
| `TRANSACTION_DEST_WALLET_NOT_ALLOWED` | `TRANSACTION_DEST_WALLET_NOT_ALLOWED` | Thêm prefix |
| `TRANSACTION_LINKED_TO_DEBT` | *(giữ nguyên)* | Đã có prefix |
| `TRANSACTION_IMPORT_ROWS_EXCEEDED` | `TRANSACTION_IMPORT_ROWS_EXCEEDED` | Thêm prefix + rõ nghĩa import |

### 4.5 Ngân sách (2 mã đổi, 1 giữ nguyên)

| Tên hiện tại | Tên mới | Lý do |
|---|---|---|
| `BUDGET_ALREADY_EXISTS` | *(giữ nguyên)* | Đã có prefix |
| `BUDGET_CATEGORY_NOT_EXPENSE` | `BUDGET_CATEGORY_NOT_EXPENSE` | Thêm prefix |
| `BUDGET_CATEGORY_NOT_EDITABLE` | `BUDGET_CATEGORY_NOT_EDITABLE` | Thêm prefix |

### 4.6 Xuất tệp (1 mã đổi, 1 giữ nguyên)

| Tên hiện tại | Tên mới | Lý do |
|---|---|---|
| `EXPORT_LINK_EXPIRED` | *(giữ nguyên)* | Đã có prefix |
| `EXPORT_FORMAT_NOT_SUPPORTED` | `EXPORT_FORMAT_NOT_SUPPORTED` | Thêm prefix |

### 4.7 Sổ nợ (2 mã đổi, 2 giữ nguyên)

| Tên hiện tại | Tên mới | Lý do |
|---|---|---|
| `DEBT_ALREADY_SETTLED` | *(giữ nguyên)* | Đã có prefix |
| `DEBT_WRITTEN_OFF` | *(giữ nguyên)* | Đã có prefix |
| `DEBT_PAYMENT_EXCEEDS_REMAINING` | `DEBT_PAYMENT_EXCEEDS_REMAINING` | Thêm prefix + rõ nghĩa |
| `DEBT_DUE_DATE_INVALID` | `DEBT_DUE_DATE_INVALID` | Thêm prefix + trạng thái về cuối |

### 4.8 Định kỳ (5 mã đổi)

| Tên hiện tại | Tên mới | Lý do |
|---|---|---|
| `RECURRING_FREQUENCY_INVALID` | `RECURRING_FREQUENCY_INVALID` | Thêm prefix + trạng thái về cuối |
| `RECURRING_INTERVAL_INVALID` | `RECURRING_INTERVAL_INVALID` | Thêm prefix + trạng thái về cuối |
| `RECURRING_TYPE_INVALID` | `RECURRING_TYPE_INVALID` | Thêm prefix + trạng thái về cuối |
| `RECURRING_END_DATE_INVALID` | `RECURRING_END_DATE_INVALID` | Thêm prefix + trạng thái về cuối |
| `RECURRING_ALREADY_RUN_TODAY` | `RECURRING_ALREADY_RUN_TODAY` | Thêm prefix |

### 4.9 Mục tiêu (1 mã đổi, 3 giữ nguyên)

| Tên hiện tại | Tên mới | Lý do |
|---|---|---|
| `GOAL_TARGET_DATE_INVALID` | `GOAL_TARGET_DATE_INVALID` | Thêm prefix + trạng thái về cuối |
| `GOAL_ALREADY_COMPLETED` | *(giữ nguyên)* | Đã có prefix |
| `GOAL_CANCELLED` | *(giữ nguyên)* | Đã có prefix |
| `GOAL_WALLET_REQUIRED` | *(giữ nguyên)* | Đã có prefix |

### 4.10 Nhóm — chung (17 mã đổi, 6 giữ nguyên, 1 xóa)

| Tên hiện tại | Tên mới | Lý do |
|---|---|---|
| `GROUP_NOT_FOUND` | *(giữ nguyên)* | Đã có prefix |
| `GROUP_NOT_ARCHIVED` | *(giữ nguyên)* | Đã có prefix |
| `GROUP_FUND_NOT_FOUND` | *(giữ nguyên)* | Đã có prefix |
| `GROUP_MEMBER_NOT_FOUND` | *(giữ nguyên)* | Đã có prefix |
| `GROUP_ARCHIVED` | *(giữ nguyên)* | Đã có prefix |
| `GROUP_SETTLEMENT_REQUIRED` | *(giữ nguyên)* | Đã có prefix, nghĩa rõ |
| `GROUP_MEMBER_NOT_IN_GROUP` | `GROUP_MEMBER_NOT_IN_GROUP` | **Sửa typo** + chuẩn hóa |
| `GROUP_MEMBER_ALREADY_EXISTS` | `GROUP_MEMBER_ALREADY_EXISTS` | Thêm prefix |
| `GROUP_MEMBER_PENDING` | `GROUP_MEMBER_PENDING` | Thêm prefix + rút gọn |
| `GROUP_MEMBER_REQUIRED` | `GROUP_MEMBER_REQUIRED` | Bỏ `FORBIDDEN_` ở đầu — gộp nghĩa với `GROUP_MEMBER_REQUIRED` |
| `GROUP_OWNER_REQUIRED` | `GROUP_OWNER_REQUIRED` | Bỏ `FORBIDDEN_` ở đầu |
| `GROUP_TREASURER_REQUIRED` | `GROUP_TREASURER_REQUIRED` | Bỏ `FORBIDDEN_` ở đầu |
| `GROUP_DELETE_BALANCE_NOT_ZERO` | `GROUP_DELETE_BALANCE_NOT_ZERO` | Chuẩn hóa — bỏ câu lệnh `CANNOT_` |
| `GROUP_OWNER_NOT_REMOVABLE` | `GROUP_OWNER_NOT_REMOVABLE` | Chuẩn hóa |
| `GROUP_PENDING_TXN_EXIST` | `GROUP_PENDING_TXN_EXIST` | Rút gọn TRANSACTIONS |
| `GROUP_PENDING_TXN_EXIST` | *(XÓA)* | **Trùng lặp** hoàn toàn với mã trên |
| `GROUP_MEMBER_SHARE_NOT_ZERO` | `GROUP_MEMBER_SHARE_NOT_ZERO` | Thêm prefix |
| `GROUP_OWNER_TRANSFER_REQUIRED` | `GROUP_OWNER_TRANSFER_REQUIRED` | Chuẩn hóa — bỏ `MUST_..._FIRST` |
| `GROUP_TREASURER_TRANSFER_REQUIRED` | `GROUP_TREASURER_TRANSFER_REQUIRED` | Chuẩn hóa |
| `GROUP_FUND_HOLDER_NOT_MEMBER` | `GROUP_FUND_HOLDER_NOT_MEMBER` | Thêm prefix |
| `GROUP_NEW_OWNER_NOT_MEMBER` | `GROUP_NEW_OWNER_NOT_MEMBER` | Thêm prefix |
| `GROUP_TRANSFER_TO_SELF` | `GROUP_TRANSFER_TO_SELF` | Chuẩn hóa — bỏ `CANNOT_` |
| `GROUP_MINIMUM_MEMBERS_REQUIRED` | `GROUP_MINIMUM_MEMBERS_REQUIRED` | **Sửa câu tối nghĩa** |
| `GROUP_OWNER_IN_MEMBER_LIST` | `GROUP_OWNER_IN_MEMBER_LIST` | **Sửa sai ngữ pháp** |

### 4.11 Nhóm — giao dịch nhóm (22 mã đổi, 1 giữ nguyên)

| Tên hiện tại | Tên mới | Lý do |
|---|---|---|
| `GROUP_TRANSACTION_NOT_FOUND` | *(giữ nguyên)* | Đã có prefix, tên rõ |
| `GROUP_TXN_ACCESS_DENIED` | `GROUP_TXN_ACCESS_DENIED` | **Sửa ngữ pháp** + message cũ sai chính tả |
| `GROUP_TXN_CATEGORY_REQUIRED` | `GROUP_TXN_CATEGORY_REQUIRED` | Thêm prefix |
| `GROUP_TXN_SYSTEM_CATEGORY_REQUIRED` | `GROUP_TXN_SYSTEM_CATEGORY_REQUIRED` | Thêm prefix |
| `GROUP_TXN_PARTICIPANTS_SUM_MISMATCH` | `GROUP_TXN_PARTICIPANTS_SUM_MISMATCH` | Thêm prefix |
| `GROUP_TXN_PARTICIPANTS_AMOUNT_INVALID` | `GROUP_TXN_PARTICIPANTS_AMOUNT_INVALID` | Thêm prefix |
| `GROUP_TXN_PARTICIPANT_DATA_INVALID` | `GROUP_TXN_PARTICIPANT_DATA_INVALID` | Thêm prefix + trạng thái về cuối |
| `GROUP_TXN_DATE_IN_FUTURE` | `GROUP_TXN_DATE_IN_FUTURE` | Thêm prefix |
| `GROUP_TXN_TYPE_NOT_ALLOWED` | `GROUP_TXN_TYPE_NOT_ALLOWED` | Thêm prefix |
| `GROUP_TXN_MONEY_SOURCE_INVALID` | `GROUP_TXN_MONEY_SOURCE_INVALID` | Thêm prefix |
| `GROUP_TXN_PAYER_NOT_MEMBER` | `GROUP_TXN_PAYER_NOT_MEMBER` | Thêm prefix |
| `GROUP_TXN_PARTICIPANT_NOT_MEMBER` | `GROUP_TXN_PARTICIPANT_NOT_MEMBER` | Thêm prefix |
| `GROUP_TXN_PARTICIPANT_DUPLICATED` | `GROUP_TXN_PARTICIPANT_DUPLICATED` | **Sửa tối nghĩa** — "conflict" → "duplicated" |
| `GROUP_TXN_PARTICIPANTS_SHARE_MIXED` | `GROUP_TXN_PARTICIPANTS_SHARE_MIXED` | Thêm prefix |
| `GROUP_TXN_PARTICIPANTS_NOT_ALLOWED` | `GROUP_TXN_PARTICIPANTS_NOT_ALLOWED` | Thêm prefix |
| `GROUP_TXN_PARTICIPANTS_REQUIRED` | `GROUP_TXN_PARTICIPANTS_REQUIRED` | Thêm prefix + rút gọn (message giữ ngữ cảnh đầy đủ) |
| `GROUP_TXN_EDIT_FORBIDDEN` | `GROUP_TXN_EDIT_FORBIDDEN` | Bỏ `FORBIDDEN_` ở đầu |
| `GROUP_TXN_CONFIRMED_EDIT_FORBIDDEN` | `GROUP_TXN_CONFIRMED_EDIT_FORBIDDEN` | Bỏ `FORBIDDEN_` ở đầu |
| `GROUP_TXN_DELETE_FORBIDDEN` | `GROUP_TXN_DELETE_FORBIDDEN` | Bỏ `FORBIDDEN_` ở đầu |
| `GROUP_TXN_NOT_PENDING` | `GROUP_TXN_NOT_PENDING` | Thêm prefix |
| `GROUP_TXN_AMOUNT_EXCEEDS_SHARE` | `GROUP_TXN_AMOUNT_EXCEEDS_SHARE` | Thêm prefix |
| `GROUP_TXN_REFUND_EXCEEDS_BALANCE` | `GROUP_TXN_REFUND_EXCEEDS_BALANCE` | Thêm prefix + chuẩn hóa |
| `GROUP_TXN_DELETE_OWNER_ONLY` | `GROUP_TXN_DELETE_OWNER_ONLY` | Chuẩn hóa — bỏ câu lệnh |
| `GROUP_TXN_ADJUSTMENT_REASON_REQUIRED` | `GROUP_TXN_ADJUSTMENT_REASON_REQUIRED` | Thêm prefix |
| `GROUP_TXN_ADJUSTMENT_NOT_EDITABLE` | `GROUP_TXN_ADJUSTMENT_NOT_EDITABLE` | Thêm prefix |

---

## 5. Tổng kết số lượng

| Hạng mục | Số lượng |
|----------|---------|
| Tổng mã hiện tại | 103 |
| Giữ nguyên | 33 |
| Đổi tên | 69 |
| Xóa (trùng lặp) | 1 |
| **Tổng mã sau khi đổi** | **102** |

---

## 6. Các file sẽ thay đổi

### Backend — main (46 file)

| Package | File | Số mã dùng |
|---------|------|-----------|
| `common/exception` | `ErrorCode.java` | Tất cả — file gốc |
| `common/exception` | `GlobalExceptionHandler.java` | ~5 |
| `common/idempotency` | `IdempotencyAspect.java` | 1 |
| `auth/service/impl` | `AuthServiceImpl.java` | ~5 |
| `auth/service/impl` | `EmailLoginImpl.java` | ~4 |
| `auth/service/impl` | `GoogleServiceImpl.java` | ~3 |
| `auth/service/impl` | `TokenServiceImpl.java` | ~2 |
| `auth/helper` | `ForgotPasswordRateLimiter.java` | ~1 |
| `user/exception` | 7 file exception | mỗi file 1 |
| `user/service/impl` | 4 file service | ~10 tổng |
| `wallet/service/impl` | 2 file service | ~8 tổng |
| `category/service/impl` | 2 file service | ~10 tổng |
| `transaction/service/impl` | 2 file service | ~12 tổng |
| `budget/service` | 2 file service | ~4 tổng |
| `debt/service/impl` | 1 file | ~4 |
| `goal/service/impl` | 1 file | ~4 |
| `recurring/service/impl` | 2 file | ~5 |
| `report/service/impl` | 1 file | ~2 |
| `notification/service/impl` | 1 file | ~1 |
| `group/service/impl` | 6 file service | ~30 tổng |
| `group/helper` | 2 file | ~8 tổng |
| `group/validator` | 3 file | ~10 tổng |
| `group/service/impl/strategy` | 4 file strategy | ~8 tổng |

### Backend — test (19 file)

Tất cả file test dùng `ErrorCode.*` trong assertion — đổi theo tên mới.

### Tài liệu

| File | Thay đổi |
|------|----------|
| `docs/design/group/api.md` | Cập nhật mã lỗi nhóm |
| `api/*.md` (nếu có nhắc mã lỗi) | Cập nhật theo tên mới |

### Flutter (thông báo, không sửa trong commit này)

| File | Thay đổi |
|------|----------|
| `lib/core/network/api_error.dart` | Cập nhật mapping mã lỗi |
| Các file xử lý lỗi cụ thể | Đổi tên mã lỗi |

---

## 7. Thứ tự thực hiện

```
1. Đổi tên enum trong ErrorCode.java
2. IDE find-replace toàn bộ src/main/java (compile check — sót là fail build)
3. IDE find-replace toàn bộ src/test/java
4. Chạy mvn compile — phải pass
5. Chạy mvn test — phải pass (chỉ test liên quan)
6. Grep api/*.md và docs/ — cập nhật mã lỗi trong tài liệu
7. Gửi bảng mapping cho Flutter để sửa đồng bộ
```

---

## 8. Điểm cần xác nhận

<!-- Đánh dấu ✅ hoặc comment bên cạnh -->

- [ ] Đồng ý convention `[MODULE]_[CHỦ_THỂ]_[TRẠNG_THÁI]`?
- [ ] Đồng ý dùng `GROUP_TXN_` thay vì `GROUP_TRANSACTION_` cho giao dịch nhóm?
- [ ] Có mã nào trong bảng mapping muốn đổi tên khác?
- [ ] Xóa `GROUP_PENDING_TXN_EXIST` (trùng) — OK?
- [ ] Gộp `GROUP_MEMBER_REQUIRED` + `GROUP_MEMBER_REQUIRED` thành `GROUP_MEMBER_REQUIRED` — OK?
- [ ] Sửa luôn message sai chính tả của `GROUP_TXN_ACCESS_DENIED` ("Chỉ ch nhóm") — OK?

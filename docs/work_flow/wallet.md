# Workflow — Ví (wallet)

Code chính: [WalletBehavierImpl](../../src/main/java/com/datn/financeapp/module/wallet/service/impl/WalletBehavierImpl.java) (API ví),
[WalletServiceImpl](../../src/main/java/com/datn/financeapp/module/wallet/service/impl/WalletServiceImpl.java) (hàm cho module khác gọi),
[ReconcilHistoryImpl](../../src/main/java/com/datn/financeapp/module/wallet/service/impl/ReconcilHistoryImpl.java) (lịch sử đối chiếu).

**Quy tắc số dư:** số dư ví = tổng giao dịch chưa xoá của ví (thu `+`, chi và cho vay `−`). Java **không** tự cộng trừ số dư; mọi thay đổi số dư do trigger trong DB.
`Wallet.balance` có `updatable = false` nên `save(wallet)` không ghi đè số dư.

| Trigger | File | Khi nào chạy |
|---|---|---|
| `trg_users_default_wallet` | [V6](../../src/main/resources/db/migration_v2/V6__trigger_wallet_default.sql) | Có user mới → tạo ví "Tiền mặt" mặc định |
| `trg_wallets_only_one_default` | [V6](../../src/main/resources/db/migration_v2/V6__trigger_wallet_default.sql) | Thêm ví, đổi `is_default`/`is_deleted` → lúc commit kiểm user có đúng 1 ví mặc định chưa xoá |
| `trg_wallets_initial_transaction` | [V7](../../src/main/resources/db/migration_v2/V7__trigger_wallet_balance.sql) | Ví mới có số dư > 0 → đưa số dư về 0 rồi sinh giao dịch thu "Số dư ban đầu" |
| `trg_transactions_wallet_balance` | [V7](../../src/main/resources/db/migration_v2/V7__trigger_wallet_balance.sql) | Giao dịch thay đổi → cộng trừ số dư ví |
| `trg_wallets_soft_delete_cascade` | [V8](../../src/main/resources/db/migration_v2/V8__trigger_wallet_soft_delete.sql) | Ví bị xoá mềm → xoá mềm giao dịch, tắt định kỳ của ví |

## Tạo ví — `POST /wallets` (`@Idempotent`)

Admin tạo hộ: `POST /wallets/admin/{user_id}` (quyền `wallet:update`).

```
create
-> userId lấy từ token (admin thì lấy từ đường dẫn)
-> trùng tên với ví chưa xoá của user (phân biệt hoa thường) → WALLET_NAME_EXISTS
-> có icon_id → icon phải tồn tại và đang bật, không thì ICON_NOT_FOUND
-> insert wallets (is_default = false, balance = số nhập, không âm)
   -> [trigger] balance > 0:
        đưa balance về 0
        -> insert giao dịch INCOME "Số dư ban đầu" (không danh mục)
           -> [trigger] cộng lại vào ví → balance = số nhập
   -> [trigger, lúc commit] user vẫn đúng 1 ví mặc định
-> trả thông tin ví
```

## Danh sách ví — `GET /wallets`

```
gets
-> ví chưa xoá của user, phân trang (?page=1&page_size=20)
-> lấy icon của cả trang trong một lần gọi
   (ví không chọn icon hoặc icon đã bị admin tắt → icon rỗng, không lỗi)
-> trả danh sách + page_meta
```

Admin: `GET /wallets/admin/{user_id}` (ví chưa xoá của một user), `GET /wallets/admin/all` (mọi ví, **kể cả ví đã xoá**). Cả hai cần quyền `wallet:read`.

Chưa có API xem chi tiết một ví (`WalletBehavierImpl.get` có sẵn nhưng controller chưa gắn đường dẫn).

## Sửa ví — `PATCH /wallets/{id}`

Admin: `PATCH /wallets/admin/{id}?user_id=...` (quyền `wallet:update`).

```
update
-> ví phải của user và chưa xoá (không → NOT_FOUND)
-> có name:
     đổi sang tên khác và trùng ví chưa xoá → WALLET_NAME_EXISTS
     (gửi lại đúng tên cũ thì bỏ qua bước kiểm)
-> có icon_id → icon phải tồn tại và đang bật, không thì ICON_NOT_FOUND
   không có icon_id → giữ icon cũ (icon cũ đã bị tắt thì trả rỗng)
-> có color → đổi màu
-> save (không đổi được balance, type)
```

Muốn thay đổi số dư thì tạo giao dịch thu/chi, không sửa số dư trực tiếp.

## Xoá ví — `DELETE /wallets/{id}`

Admin: `DELETE /wallets/admin/{id}?user_id=...` (quyền `wallet:delete`).

```
softDelete
-> ví phải của user và chưa xoá (không → NOT_FOUND)
-> ví mặc định → WALLET_DEFAULT_NOT_DELETABLE
-> wallets.is_deleted = true
   -> [trigger] xoá mềm mọi giao dịch của ví
        -> [trigger] mỗi giao dịch trừ khỏi số dư → số dư ví về 0
   -> [trigger] tắt giao dịch định kỳ của ví (is_enabled = false)
   -> [trigger] buổi lịch PENDING của các định kỳ đó → SKIPPED
```

## Lịch sử đối chiếu

| API | Ai gọi | Trả về |
|---|---|---|
| `GET /wallets/{id}/reconciliations` | Chủ ví (ví người khác → `NOT_FOUND`) | Các lần job đối chiếu phải sửa số dư của ví này |
| `GET /wallets/admin/reconciliations` | Admin (quyền `wallet:read`) | Mọi lần sửa của mọi ví |

Mỗi dòng gồm: ví, `wallet_balance` (số dư trước khi sửa), `total_txn` (số dư đúng), `reconciled_at`. Lịch sử do job đối chiếu ghi, xem [scheduler.md](scheduler.md).

## Hàm cho module khác gọi (`WalletService`)

| Hàm | Ai gọi | Làm gì |
|---|---|---|
| `findIfMyWallet(userId, walletId)` | Giao dịch (tạo, xem, sửa) | Ví phải của user và chưa xoá, không thì `NOT_FOUND`. Icon đã tắt chỉ để trống |
| `findSumary(walletId, userId)` | Giao dịch định kỳ, lịch sử đối chiếu | Như trên, trả bản tóm tắt |
| `findSumary(walletId)` | Lịch sử đối chiếu của admin | Tìm theo id, không kiểm chủ |
| `findDefaultWallet(userId)` | Hiện chưa module nào gọi | Ví mặc định của user |
| `reconcileAll()` | Job đối chiếu | Xem [scheduler.md](scheduler.md) |

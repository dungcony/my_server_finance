# Workflow — Giao dịch cá nhân (transaction)

Code chính: [TxnBehavierImpl](../../src/main/java/com/datn/financeapp/module/transaction/service/impl/TxnBehavierImpl.java),
[TxnController](../../src/main/java/com/datn/financeapp/module/transaction/controller/TxnController.java),
[TxnSpecifial](../../src/main/java/com/datn/financeapp/module/transaction/helper/TxnSpecifial.java) (bộ lọc).

**Số dư ví do trigger lo:** mọi thay đổi trên bảng `transactions` đều chạy `trg_transactions_wallet_balance`
([V7](../../src/main/resources/db/migration_v2/V7__trigger_wallet_balance.sql)). Java không cộng trừ số dư.

| Loại giao dịch | Ảnh hưởng số dư ví |
|---|---|
| `INCOME` (thu) | `+ amount` |
| `EXPENSE` (chi) | `− amount` |
| `LOAN` (cho vay) | `− amount` |

## Tạo giao dịch — `POST /transactions` (`@Idempotent`)

```
create
-> userId lấy từ token
-> amount phải > 0 (không → TRANSACTION_AMOUNT_INVALID)
-> phải có category_id (không → TRANSACTION_CATEGORY_REQUIRED)
-> danh mục phải tồn tại, chưa xoá, là lá (không → CATEGORY_NOT_FOUND / TRANSACTION_CATEGORY_NOT_LEAF)
-> ví phải của user và chưa xoá (không → NOT_FOUND)
-> insert transactions (is_deleted = false, source = MANUAL)
   -> [trigger] cộng trừ số dư ví theo loại
-> trả chi tiết giao dịch kèm ví và danh mục
```

Thông tin ví trong response được đọc **trước** khi trigger chạy, nên số dư hiển thị là số dư cũ.

## Danh sách — `GET /transactions`

```
gets
-> luôn lọc: giao dịch của user, chưa xoá
-> bộ lọc tuỳ chọn (query param snake_case):
     from, to                 thời điểm phát sinh (occurred_at)
     type                     EXPENSE / INCOME / LOAN
     wallet_id, category_id   (category_id chỉ lọc đúng danh mục đó, không gộp danh mục con)
     search                   tìm trong name, note (không phân biệt hoa thường)
     min_amount, max_amount
     source                   MANUAL / RECURRING (đang lỗi, xem cuối file)
-> phân trang ?page=1&page_size=20, mặc định sắp theo created_at giảm dần
-> trả danh sách tóm tắt + page_meta
```

Admin: `GET /transactions/admin` (quyền `transactions:read`), cùng bộ lọc, `user_id` tuỳ chọn (không gửi thì lấy mọi user).

## Xem chi tiết — `GET /transactions/{id}`

```
detail
-> giao dịch của user, chưa xoá (không → NOT_FOUND)
-> lấy danh mục (phải tồn tại, chưa xoá, là lá) và ví
-> trả chi tiết
```

Admin: `GET /transactions/admin/{id}` (quyền `transactions:read`).

## Sửa giao dịch — `PATCH /transactions/{id}`

```
update
-> giao dịch của user, chưa xoá (không → NOT_FOUND)
-> có wallet_id → đổi ví, ví mới phải của user
-> có category_id → đổi danh mục, phải là lá
-> có date → không được ở tương lai (TRANSACTION_DATE_IN_FUTURE), lưu 00:00 giờ VN của ngày đó
-> đổi type, amount, display_name, note nếu có gửi
-> save
   -> [trigger] trừ ảnh hưởng cũ khỏi ví cũ, cộng ảnh hưởng mới vào ví mới
-> trả chi tiết
```

## Xoá giao dịch — `DELETE /transactions/{id}`

`DELETE /transactions/bulk` tạm gỡ khỏi controller, `bulkDelete` bên dưới sẽ hoàn thiện sau.

```
softDelete
-> giao dịch của user (kể cả đã xoá) (không → NOT_FOUND)
-> đã xoá rồi → trả thành công luôn (gọi lại nhiều lần vẫn được)
-> is_deleted = true
   -> [trigger] trừ ảnh hưởng của giao dịch khỏi số dư ví

bulkDelete (body là danh sách id)
-> xoá lần lượt từng id như trên, trong cùng một transaction
-> một id không tìm thấy → rollback cả lô
```

Admin: `DELETE /transactions/admin/{id}` (quyền `transactions:delete`).

## Giao dịch do hệ thống tự tạo hoặc tự xoá

| Nguồn | Làm gì | Ở đâu |
|---|---|---|
| Tạo ví có số dư > 0 | Sinh giao dịch INCOME "Số dư ban đầu", không có danh mục | Trigger `trg_wallets_initial_transaction` ([V7](../../src/main/resources/db/migration_v2/V7__trigger_wallet_balance.sql)) |
| Giao dịch định kỳ tới hạn | Sinh giao dịch theo cấu hình định kỳ | Scheduler, xem [recurring.md](recurring.md) |
| Xoá ví | Xoá mềm mọi giao dịch của ví | Trigger `trg_wallets_soft_delete_cascade` ([V8](../../src/main/resources/db/migration_v2/V8__trigger_wallet_soft_delete.sql)) |

## Lỗi đang mở (xem [review-03](../problem/review-03-transaction.md))

- T2: lọc theo `source` lỗi vì entity `Transaction` chưa có field `source`.
- T3: giao dịch "Số dư ban đầu" (không có danh mục) và giao dịch có danh mục đã xoá / không còn là lá thì không xem, sửa được.
- T5: sửa giao dịch gửi `type` EXPENSE/INCOME kèm `category_id` mới lại bị báo thiếu danh mục (điều kiện bị ngược).
- T7: so "ngày tương lai" theo giờ UTC thay vì giờ VN.

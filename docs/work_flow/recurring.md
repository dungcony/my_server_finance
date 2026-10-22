# Workflow — Giao dịch định kỳ (recurring)

Code chính: [RecTxnBehavierImpl](../../src/main/java/com/datn/financeapp/module/transaction/service/impl/RecTxnBehavierImpl.java) (cấu hình định kỳ),
[RecTxnScheduleImpl](../../src/main/java/com/datn/financeapp/module/transaction/service/impl/RecTxnScheduleImpl.java) (sinh lịch, chạy lịch),
[RecTxnScheduleBehavierImpl](../../src/main/java/com/datn/financeapp/module/transaction/service/impl/RecTxnScheduleBehavierImpl.java) (API buổi lịch),
[RecScheduleCalculator](../../src/main/java/com/datn/financeapp/module/transaction/helper/RecScheduleCalculator.java) (tính mốc kế tiếp).

**Hai bảng:**

| Bảng | Là gì |
|---|---|
| `recurring_transactions` | Cấu hình: ví, danh mục, loại, số tiền, tần suất, bước lặp, ngày bắt đầu/kết thúc, bật/tắt |
| `recurring_transaction_schedules` | Từng buổi sẽ chạy: thời điểm `run_at`, số tiền riêng của buổi, trạng thái `PENDING` / `SUCCESS` / `FAILED` / `SKIPPED` |

Tần suất: `HOUR`, `DAY`, `WEEK`, `MONTH`, `QUARTER` (3 tháng), `YEAR`. Mốc kế tiếp tính theo lịch thực tế, giờ Việt Nam (cộng tháng thật, không phải cộng 30 ngày).

## Tạo định kỳ — `POST /recurring`

```
create
-> userId lấy từ token
-> map request → recurring (is_enabled = true)
-> danh mục phải tồn tại, chưa xoá, là lá
-> ví phải của user và chưa xoá (không → NOT_FOUND)
-> insert recurring_transactions
-> sinh sẵn các buổi lịch (đều PENDING, số tiền = số tiền cấu hình):
     buổi đầu = start_at, buổi sau = buổi trước + bước lặp × tần suất
     không có end_at → sinh 12 buổi
     có end_at      → sinh tới end_at
-> trả chi tiết định kỳ
```

`start_at` không được ở tương lai (`@PastOrPresent`); `end_at` nếu có phải ở tương lai.

## Xem, sửa, bật/tắt, xoá

| API | Làm gì |
|---|---|
| `GET /recurring/{id}` | Xem chi tiết cấu hình kèm ví, danh mục |
| `PATCH /recurring/{id}` | Sửa tên, số tiền, ví, danh mục, ghi chú, `end_at`; tần suất và bước lặp phải gửi cùng nhau. Đồng bộ các buổi chưa chạy, xem [Sửa định kỳ](#sửa-định-kỳ) |
| `POST /recurring/{id}/enable` | `is_enabled = true` |
| `POST /recurring/{id}/disable` | `is_enabled = false` (buổi lịch vẫn giữ `PENDING`, scheduler bỏ qua) |
| `DELETE /recurring/{id}` | Xoá mềm cấu hình (buổi lịch vẫn giữ nguyên) |

Admin: `GET /recurring/admin/{id}`, `GET /recurring/admin/{recId}/schedules` (quyền `recurring_txn:read`); `DELETE /recurring/admin/{id}`, `DELETE /recurring/admin/schedules/{scheduleId}` (quyền `recurring_txn:delete`).

## Sửa định kỳ

Buổi chưa chạy = mọi buổi `PENDING` + buổi `SKIPPED` có `run_at` ở tương lai. Buổi `SUCCESS`, `FAILED`, `SKIPPED` đã qua giữ nguyên.

```
update
-> so giá trị mới với giá trị cũ, ghi cấu hình
-> [event RecTxnUpdateEvent, cùng transaction]
     đổi tần suất / bước lặp / end_at
       -> xoá hẳn các buổi chưa chạy
       -> mốc = buổi đã chạy gần nhất + 1 bước mới (chưa chạy buổi nào thì từ start_at)
       -> sinh lại buổi PENDING như lúc tạo
     chỉ đổi số tiền
       -> cập nhật amount cho các buổi chưa chạy, giữ nguyên ngày và ID
     đổi ví, danh mục, tên, ghi chú → không đụng buổi lịch (scheduler đọc trực tiếp từ định kỳ)
```

Sửa riêng từng buổi (số tiền, ngày, `SKIPPED`) sẽ bị đè khi sửa định kỳ. Kéo dài `end_at` không tự bật lại định kỳ đã tự tắt.

## Buổi lịch

| API | Làm gì |
|---|---|
| `GET /recurring/{recId}/schedules` | Danh sách buổi lịch của user, sắp theo `run_at` tăng dần |
| `POST /recurring/{recId}/schedules` | Thêm một buổi riêng (thời điểm, số tiền), mặc định `PENDING` |
| `PATCH /recurring/schedules/{scheduleId}` | Đổi thời điểm, số tiền, trạng thái của một buổi (VD: `SKIPPED` để bỏ buổi đó) |
| `DELETE /recurring/schedules/{scheduleId}` | Xoá mềm một buổi |

## Chạy buổi lịch tới hạn (scheduler, mỗi giờ)

Chi tiết lịch chạy xem [scheduler.md](scheduler.md).

```
processDueSchedules
-> lấy các buổi PENDING, chưa xoá, run_at <= bây giờ
-> với từng buổi:
     định kỳ đã tắt hoặc đã xoá → bỏ qua (buổi vẫn PENDING)
     -> insert transactions (ví, danh mục, loại của định kỳ; số tiền của buổi; occurred_at = run_at)
        -> [trigger] cộng trừ số dư ví
     -> buổi → SUCCESS
     -> định kỳ không có end_at → thêm một buổi PENDING mới sau buổi xa nhất (luôn giữ đủ buổi phía trước)
        định kỳ có end_at và hết buổi PENDING → tắt định kỳ (is_enabled = false)
```

## Khi xoá ví

Trigger `trg_wallets_soft_delete_cascade` ([V8](../../src/main/resources/db/migration_v2/V8__trigger_wallet_soft_delete.sql)) tắt mọi định kỳ của ví và chuyển các buổi `PENDING` sang `SKIPPED`, để scheduler không sinh giao dịch vào ví đã xoá.

## Lỗi đang mở (xem [review-04](../problem/review-04-recurring.md))

- R1: xem, sửa định kỳ không kiểm chủ (tìm bằng `findById`).
- R2: scheduler chạy cả lô trong một transaction, một buổi lỗi là rollback cả lô; buổi lỗi không bao giờ thành `FAILED`.
- R3: `start_at` bị chặn ở tương lai; chọn ngày bắt đầu trong quá khứ thì scheduler sinh bù dồn một lần.
- R4: có `end_at` thì sinh toàn bộ buổi một lần, không giới hạn số lượng.
- R6: tắt hoặc xoá định kỳ thì buổi `PENDING` vẫn bị quét lại mỗi giờ; bật lại thì chạy bù dồn.
- R8: API sửa buổi lịch cho đổi trạng thái tuỳ ý (VD `SUCCESS` → `PENDING` làm sinh giao dịch lần 2).
- R9: không gửi `category_id` thì tạo định kỳ báo `CATEGORY_NOT_FOUND`.

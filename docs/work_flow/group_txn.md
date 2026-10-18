# Workflow — Giao dịch nhóm (group transaction)

Code
chính: [GroupTxnCreateImpl](../../src/main/java/com/datn/financeapp/module/group/service/impl/GroupTxnCreateImpl.java)
(tạo),
[GroupTxnUpdateImpl](../../src/main/java/com/datn/financeapp/module/group/service/impl/GroupTxnUpdateImpl.java) (sửa,
duyệt),
[GroupTxnDeleteImpl](../../src/main/java/com/datn/financeapp/module/group/service/impl/GroupTxnDeleteImpl.java) (xoá),
[GroupTxnGetImpl](../../src/main/java/com/datn/financeapp/module/group/service/impl/GroupTxnGetImpl.java) (xem),
[GroupTxnHelper](../../src/main/java/com/datn/financeapp/module/group/helper/GroupTxnHelper.java)
và [BalanceCalculator](../../src/main/java/com/datn/financeapp/module/group/helper/BalanceCalculator.java) (tính tiền),
[GReportServiceImpl](../../src/main/java/com/datn/financeapp/module/group/service/impl/GReportServiceImpl.java) (báo
cáo).

Vai trò (chủ nhóm, thủ quỹ, thành viên) xem [group_member.md](group_member.md).

## Khái niệm

| Khái niệm                     | Giá trị                                                                                                                                                                      |
|-------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Loại `type`                   | `EXPENSE` (chi tiêu chung), `CONTRIBUTION` (nộp quỹ), `REFUND` (quỹ trả lại tiền cho thành viên), `ADJUSTMENT_UP` / `ADJUSTMENT_DOWN` (kiểm kê: tiền thật nhiều / ít hơn sổ) |
| Nguồn tiền `money_source`     | `FUND` (tiền quỹ, không có người chi), `PERSONAL` (tiền túi của `transactor_id`)                                                                                             |
| Trạng thái `status`           | `PENDING` (chờ duyệt, **chưa tính tiền**), `CONFIRMED` (đã tính tiền), `REJECTED`                                                                                            |
| Người tham gia `participants` | Danh sách `{user_id, share_amount}`; tổng `share_amount` phải bằng `amount`                                                                                                  |

**Hai loại số dư**, chỉ thay đổi khi giao dịch `CONFIRMED`:

- **Số dư quỹ** `group_funds.current_balance`.
- **Số dư thành viên** `group_member_balances`: `contribution` (đã góp/ứng), `refund` (đã được trả lại), `share` (phần
  phải chịu). **Còn trong quỹ** `net = contribution − refund − share` (dương: nhóm nợ thành viên, âm: thành viên còn
  thiếu).

| Giao dịch                 | Số dư quỹ  | Số dư thành viên                                                                |
|---------------------------|------------|---------------------------------------------------------------------------------|
| `CONTRIBUTION` (PERSONAL) | `+ amount` | người nộp: `contribution + amount`                                              |
| `EXPENSE` từ `FUND`       | `− amount` | từng người tham gia: `share + share_amount`                                     |
| `EXPENSE` từ `PERSONAL`   | không đổi  | người chi: `contribution + amount`; từng người tham gia: `share + share_amount` |
| `REFUND` (FUND)           | `− amount` | từng người được trả: `refund + share_amount`                                    |
| `ADJUSTMENT_DOWN` (FUND)  | `− amount` | từng người tham gia: `share + share_amount`                                     |
| `ADJUSTMENT_UP` (FUND)    | `+ amount` | từng người tham gia: `share − share_amount`                                     |

Công thức này khớp với hàm `fn_rebuild_group_member_balances` (tính lại toàn bộ bảng từ lịch sử).

## Tạo giao dịch — `POST /groups/{groupId}/transactions` (`@Idempotent`)

```
create
-> người gọi phải là thành viên ACTIVE của nhóm (không → FORBIDDEN)
-> xác định trạng thái:
     thành viên thường:
        không được tạo REFUND, ADJUSTMENT (GROUP_TXN_CREATE_FORBIDDEN)
        FUND thì không có transactor; PERSONAL thì transactor phải là chính mình
        → PENDING (chờ chủ nhóm / thủ quỹ duyệt)
     chủ nhóm, thủ quỹ:
        EXPENSE, CONTRIBUTION, REFUND → CONFIRMED ngay
        ADJUSTMENT: chủ nhóm → CONFIRMED, thủ quỹ không phải chủ nhóm → PENDING
-> kiểm theo loại:
     CONTRIBUTION: nguồn PERSONAL, phải có transactor, tối đa 1 người tham gia
     EXPENSE:      phải có category_code; PERSONAL cần transactor, FUND không có transactor
     REFUND:       chỉ thủ quỹ; nguồn FUND, không transactor;
                   mỗi người không được nhận quá phần "còn trong quỹ" (net) của mình
     ADJUSTMENT:   nguồn FUND, không transactor
-> kiểm người tham gia: không trùng id, tổng share = amount,
   người tham gia và transactor đều là thành viên ACTIVE
-> insert group_transactions (+ group_transaction_participants)
-> CONFIRMED:
     cộng số dư thành viên (upsert group_member_balances)
     cộng trừ số dư quỹ
     sau commit → thông báo "Ghi vào sổ cá nhân?" cho transactor (bất đồng bộ)
   PENDING:
     sau commit → thông báo có giao dịch chờ duyệt cho thủ quỹ (bất đồng bộ)
```

Body bắt buộc có ít nhất 1 người tham gia. Với `CONTRIBUTION` gửi đúng 1 người, `share_amount = amount`.

## Duyệt giao dịch (chủ nhóm, thủ quỹ; nhóm đang `ACTIVE`)

```
POST /groups/{groupId}/transactions/status   (txn_id, status)
-> giao dịch phải đang PENDING, chưa xoá
-> ghi reviewed_by, reviewed_at, status
-> CONFIRMED: cộng số dư thành viên, cộng trừ quỹ, thông báo "Ghi vào sổ cá nhân?" sau commit
   REJECTED:  không tính tiền

POST /groups/{groupId}/transactions/statuses (body: txn_ids, status)
-> mọi id phải đang PENDING (thiếu id nào → GROUP_TXN_NOT_FOUD)
-> cập nhật cả lô, cộng số dư thành viên và quỹ một lần cho cả lô
```

## Sửa giao dịch — `PUT /groups/{groupId}/transactions/{txnId}`

```
update  (id giao dịch lấy từ txn_id trong body)
-> giao dịch của nhóm, chưa xoá; đã REJECTED → GROUP_TXN_IS_REJECT
-> người gọi là thành viên ACTIVE, nhóm đang ACTIVE
-> quyền sửa:
     không sửa được giao dịch kiểm kê (ADJUSTMENT)
     thủ quỹ sửa được mọi giao dịch
     người khác chỉ sửa giao dịch mình tạo và đang PENDING
-> đổi loại / nguồn tiền / transactor / người tham gia thì kiểm lại quy tắc theo loại (như lúc tạo)
-> đổi amount (trừ CONTRIBUTION) thì phải gửi kèm participants
-> cập nhật amount, participants, transactor, category, note, occurred_at
-> giao dịch đang CONFIRMED: số dư thành viên cộng phần chênh (sau − trước)
-> số dư quỹ cộng phần chênh
```

## Xoá giao dịch — `DELETE /groups/{groupId}/transactions/{txnId}`

```
softDelete
-> chủ nhóm hoặc thủ quỹ; nhóm chưa xoá
-> giao dịch đang CONFIRMED → hoàn lại số dư quỹ
-> trừ ảnh hưởng của giao dịch khỏi số dư thành viên
-> deleted_at = bây giờ
```

## Xem giao dịch

| API                                                       | Ai xem                                                                       | Trả về                                     |
|-----------------------------------------------------------|------------------------------------------------------------------------------|--------------------------------------------|
| `GET /groups/{groupId}/transactions`                      | Chủ nhóm, thủ quỹ                                                            | Mọi giao dịch của nhóm                     |
| `GET /groups/{groupId}/transactions/mine`                 | Mọi thành viên                                                               | Giao dịch mình tạo hoặc mình là transactor |
| `GET /groups/{groupId}/transactions/{txnId}`              | Chủ nhóm, thủ quỹ; thành viên chỉ xem giao dịch của mình hoặc đã `CONFIRMED` | Chi tiết                                   |
| `GET /groups/{groupId}/transactions/{txnId}/participants` | Như trên                                                                     | Danh sách người tham gia và phần chia      |

Nhóm đã lưu trữ vẫn xem được. Bộ lọc (query param): `statuses`, `money_source`, `type`, `transactor_id`, `create_id`,
`from_occurred_at` / `to_occurred_at` hoặc `start_date` / `end_date` (theo ngày giờ VN). Mặc định sắp theo
`occurred_at`, rồi `created_at` giảm dần.

## Kiểm kê quỹ — `POST /groups/{groupId}/fund/reconcile`

```
reconcileFund  (chỉ thủ quỹ)
-> khoá dòng quỹ (tránh 2 lần kiểm kê cùng lúc)
-> số dư thực tế bằng số dư sổ → báo lỗi, không cần kiểm kê
-> còn giao dịch PENDING → GROUP_PENDING_TXN_EXIST
-> chênh lệch = thực tế − sổ: dương → ADJUSTMENT_UP, âm → ADJUSTMENT_DOWN
-> chia đều chênh lệch cho các thành viên ACTIVE, trừ những người trong excluded_user_ids
-> tạo giao dịch kiểm kê qua luồng "Tạo giao dịch" ở trên (nguồn FUND, không transactor)
-> trả số dư sổ, số dư thực tế, chênh lệch, loại, id giao dịch kiểm kê
```

## Báo cáo (thành viên `ACTIVE`, nhóm đang `ACTIVE`)

| API                                           | Trả về                                                                                                                                                                                        |
|-----------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `GET /groups/{groupId}/summary?month=YYYY-MM` | Tổng chi (`EXPENSE`) và tổng nộp quỹ (`CONTRIBUTION`) đã `CONFIRMED`, theo tháng (giờ VN) hoặc toàn thời gian nếu không gửi `month`; kèm quỹ và mục tiêu                                      |
| `GET /groups/{groupId}/balances`              | Từng thành viên: `refund`, `share`, `net`, số cần nộp thêm (`= −net` nếu âm). Gồm thành viên `ACTIVE` và người đã rời / bị xoá còn số dư khác 0. Tổng cần nộp chỉ trả khi nhóm bật quyết toán |

## Ghi vào sổ cá nhân

Giao dịch nhóm **không tự** ghi vào ví cá nhân. Khi giao dịch `CONFIRMED`, transactor nhận thông báo
`GROUP_TXN_RECORD_REQUEST` kèm dữ liệu (loại thu/chi, số tiền, danh mục, ngày, ghi chú). Nếu đồng ý, app gọi
`POST /transactions` để tạo giao dịch cá nhân, xem [txn.md](txn.md).

## Lỗi đang mở (xem [review-05](../problem/review-05-group.md), [review-06](../problem/review-06-user-auth-notification.md))

- G2: duyệt giao dịch không lọc theo nhóm, duyệt được giao dịch nhóm khác và tiền bị cộng vào nhóm đang gọi.
- G3: duyệt hàng loạt với `REJECTED` vẫn cộng tiền và gửi thông báo ghi sổ.
- G4: gửi `status = PENDING` khi duyệt → lỗi 500 (vi phạm `ck_gt_review`).
- G5: sửa giao dịch `PENDING` vẫn làm đổi số dư quỹ.
- G6: xoá giao dịch `PENDING`/`REJECTED` vẫn trừ số dư thành viên.
- G7: sửa giao dịch không kiểm lại người tham gia, chưa kiểm hạn mức `REFUND`, đổi loại không xoá `category_id`, và trả
  về `null`.
- G8: thủ quỹ không phải chủ nhóm kiểm kê → giao dịch `PENDING` vi phạm `ck_gt_treasurer_confirmed`, lỗi 500.
- N2: thông báo `CONFIRMED` lỗi với giao dịch không có danh mục hoặc dùng tiền quỹ (transactor null); thông báo chờ
  duyệt in UUID người tạo.
- **Mới thấy khi viết workflow:**
    - `POST /transactions/status` thiếu `@RequestBody` nên Spring đọc dữ liệu từ query param (tên camelCase `txnId`,
      `status`), không đọc JSON body. App gửi body thì báo thiếu `txn_id`.
    - `PUT /transactions/{txnId}` bỏ qua `txnId` trên đường dẫn, chỉ dùng `txn_id` trong body.
    - Tạo giao dịch chỉ kiểm người gọi là thành viên `ACTIVE`, không kiểm trạng thái nhóm, nên nhóm đã lưu trữ hoặc đã
      xoá vẫn tạo được giao dịch.

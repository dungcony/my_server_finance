# Đồng bộ bộ Bruno với controller hiện tại (thêm endpoint mới, sửa đầu vào sai)

## Bối cảnh

- Controller đã đổi nhiều (module `transaction`, `wallet`, `group`, `debt`, `goal`, `budget`, `pay`, `user`), `bruno/` chưa theo kịp.
- Đã đối chiếu tự động 167 endpoint trong `*/controller/*.java` với 155 file `bruno/**/*.yml`: 47 endpoint chưa có Bruno, 37 file Bruno trỏ URL không còn, ~20 file sai tên/kiểu field đầu vào.
- Phân trang toàn cục đặt ở `WebConfig.java:28-36`: tham số `page` (bắt đầu từ 1) và `page_size`, tối đa 100.
- Yêu cầu: thêm endpoint mới vào `bruno/`, sửa các endpoint sai đầu vào.

## Quyết định đã chốt

| # | Câu hỏi | Chọn |
|---|---|---|
| 1 | File Bruno của endpoint đã bị bỏ khỏi code | **Xoá hết** |
| 2 | File có URL gần giống endpoint mới | **Sửa lại cho khớp** (không xoá) |
| 3 | Chỗ đặt endpoint admin mới | **Mỗi module 1 thư mục, tách `User/` và `Admin/`** (webhook PayOS để `15-Payments/Public/`) |
| 4 | Test | Không có test Java (chỉ đổi file `.yml`). Kiểm tra bằng script đối chiếu, bạn tự chạy Bruno |

## 1. Các file sẽ thay đổi

Tất cả nằm trong `bruno/` · **Không đổi**: code Java, `bruno/opencollection.yml`, nhóm `01-Auth`, `04-Categories`, `07-Reports/User`, `14-Icons` (trừ 1 dòng bên dưới).

### 1.1 Xoá (24 file) — endpoint không còn trong controller

| Thư mục | File | Căn cứ |
|---|---|---|
| `12-AI/User/` | `01`…`08` (cả thư mục `12-AI`) | không có controller nào dưới `/ai` |
| `03-Wallets/User/` | `02-Wallet-Summary`, `07-Reorder-Wallets`, `08-Transfer-Money`, `09-Adjust-Balance`, `10-Reconcile-Balance` | `WalletController.java` chỉ có `GET/POST /wallets`, `/{id}`, `/admin/*` |
| `05-Transactions/User/` | `02-List-Transactions-By-Date`, `05-Create-Bulk-Transactions`, `07-Duplicate-Transaction` | `TxnController.java:31-100` không có `/by-date`, `/bulk`, `/duplicate` |
| `06-Budgets/User/` | `03-Budget-Suggestion` | `BudgetController.java` không có `/suggestion` |
| `13-Groups/Member/` | `07-Get-Group-Fund` | không có `GET /groups/{id}/fund` |
| `13-Groups/Owner/` | `02-Invite-Code`, `04-Update-Member-Role`, `12-Confirm-Transaction`, `13-Reject-Transaction`, `17-Create-Refund`, `18-Update-Refund`, `19-Create-Withdrawal`, `20-Update-Withdrawal` | không có endpoint tương ứng; `12`/`13` xem mục "Ngoài phạm vi" (1) |

### 1.2 Sửa URL/đầu vào (file đã có)

| File | Từ | Thành |
|---|---|---|
| `13-Groups/Owner/06-Transfer-Ownership.yml` | `POST /groups/{}/transfer-ownership` | `PUT /groups/{{groupId}}/owner-role/{{newOwnerId}}`, bỏ body (`MemberController.java:43`) |
| `13-Groups/Owner/10-Update-Group-Fund.yml` | `PATCH /groups/{}/fund` | `PUT /groups/{{groupId}}/fund-kepper`, body `{ "keepper_id": "{{memberUserId}}" }` (`FundKepperUpdateReq`) |
| `13-Groups/Owner/11-Reconcile-Fund.yml` | body `date`, `note` | body `{ "excluded_user_ids": [] }` (`FundReconcileReq`) |
| `13-Groups/Owner/14-Bulk-Confirm-Transactions.yml` | `POST .../transactions/bulk-confirm`, `transaction_ids` | `POST .../transactions/statuses`, `{ "txn_ids": [...], "status": "CONFIRMED" }` (`BatchGroupTxnUpdateStatusReq`) |
| `13-Groups/Owner/15-Bulk-Reject-Transactions.yml` | `POST .../transactions/bulk-reject` | như trên, `"status": "REJECTED"` |
| `13-Groups/Member/04-Join-Group.yml` | body `invite_code` | `POST /groups/join?code=ABC12345`, bỏ body (`GroupController.java:57`) |
| `13-Groups/Member/08-Create-Group-Transaction.yml` | `date`, `category_id` | `type`, `money_source`, `amount`, `occurred_at`, `category_code`, `transactor_id`, `note`, `participants[{user_id, share_amount}]` (`GroupTxnCreateReq`) |
| `13-Groups/Member/09-List-Group-Transactions.yml` | `status`, `user_id`, `page`, `size` | `money_source`, `type`, `transactor_id`, `create_id`, `from_occurred_at`, `to_occurred_at`, `start_date`, `end_date`, `page`, `page_size` (`GroupTxnFilterReq`) |
| `13-Groups/Member/11-Update-Group-Transaction.yml` | `txn_id`, `date`, `category_id`, `user_id` | `amount`, `status`, `type`, `money_source`, `occurred_at`, `category_code`, `transactor_id`, `participants` (`GroupTxnUpdateReq`) |
| `03-Wallets/User/01-List-Wallets.yml` | `only_in_total`, `include_shared` | bỏ 2 tham số này; giữ `type`; thêm `search`, `is_default` (`WalletFillterReq`) |
| `03-Wallets/User/04-Create-Wallet.yml` | `initial_balance`, `include_in_total`, `icon` | `balance`, bỏ `include_in_total`, `icon_code` (`WalletCreateReq`) |
| `03-Wallets/User/05-Update-Wallet.yml` | `include_in_total`, `icon` | bỏ `include_in_total`, `icon_code` (`WalletupdateReq`) |
| `03-Wallets/User/06-Delete-Wallet.yml` | query `delete_transactions` | bỏ query, sửa `docs` (`WalletController.java:68-72` không nhận) |
| `05-Transactions/User/01-List-Transactions.yml` | `category_id`, `sort_by`, `sort_order` | `category_codes`; bỏ 2 tham số sort; thêm `from`, `to`, `search`, `min_amount`, `max_amount` (`TxnFilterReq`) |
| `05-Transactions/User/06-Update-Transaction.yml` | `PUT /transactions/{}` | `PATCH /transactions/{{transactionId}}`, body theo `TxnUpdateReq` ⚠️ field ngày là `occured_at` (xem "Ngoài phạm vi" 2) |
| `05-Transactions/User/08-Delete-Transaction.yml` | UUID cứng trong URL | `{{transactionId}}` |
| `06-Budgets/User/06-Create-Budget.yml` | có `wallet_id` | bỏ `wallet_id` (`CreateBudgetRequest` không có) |
| `10-Goals/User/02-Get-Goal-Detail.yml` | UUID cứng trong URL | `{{goalId}}` |
| `14-Icons/User/01-List-Icons.yml` | `size` | `page_size` |
| `07-Reports/Admin/01-Search-Export-Jobs.yml` | `size` | `page_size` |
| `09-Recurring/User/08-List-Schedules.yml`, `Admin/02-List-Schedules.yml` | `?page=0&size=20` | `?page=1&page_size=20` (phân trang từ 1) |
| 8 file đang dùng `{{admin_access}}` | `{{admin_access}}` | `{{adminAccessToken}}` (20 file khác đã dùng tên này) |
| `environments/Local.yml` | thiếu biến | thêm `adminAccessToken`, `adminRefreshToken` (giá trị rỗng, script login admin tự điền) |

### 1.3 Thêm mới (40 file)

Số thứ tự `seq` tiếp nối cuối mỗi thư mục. Admin dùng `Bearer {{adminAccessToken}}`.

| Thư mục | File mới | Endpoint |
|---|---|---|
| `02-Users/Admin/` | `11-Unlock-User` | `PATCH /admin/user/unlock` body `{ "user_id" }` |
| `03-Wallets/User/` | `11-List-Reconciliations` | `GET /wallets/{{walletId}}/reconciliations` |
| `03-Wallets/Admin/` (mới) | `01-List-User-Wallets`, `02-List-All-Wallets`, `03-Get-Wallet-Detail`, `04-Create-Wallet`, `05-Update-Wallet`, `06-Delete-Wallet`, `07-List-All-Reconciliations` | `GET /wallets/admin/{user_id}`, `/admin/all`, `/admin/detail/{id}`, `POST /admin/{user_id}`, `PATCH /admin/{id}`, `DELETE /admin/{id}`, `GET /admin/reconciliations` |
| `05-Transactions/Admin/` (mới) | `01-List-Transactions`, `02-Get-Transaction-Detail`, `03-Create-Transaction`, `04-Update-Transaction`, `05-Delete-Transaction` | `GET /transactions/admin`, `/admin/{id}`, `POST /admin?user_id=`, `PATCH /admin?txn_id=`, `DELETE /admin/{id}` |
| `06-Budgets/Admin/` (mới) | `01-List-Budgets`, `02-Get-Budget-Detail`, `03-Delete-Budget` | `GET /budgets/admin`, `/admin/{id}`, `DELETE /admin/{id}` |
| `08-Debts/Admin/` (mới) | `01-List-Debts`, `02-Debt-Summary`, `03-Get-Debt-Detail`, `04-Create-Debt`, `05-Update-Debt`, `06-Add-Payment`, `07-Cancel-Payment`, `08-Write-Off-Debt`, `09-Delete-Debt` | `/debts/admin...` (`DebtController.java:113-177`) |
| `09-Recurring/User/` | `01-List-Recurring` (khôi phục) | `GET /recurring` + query `RecTxnFilterReq` (`RecController.java:38`) |
| `10-Goals/Admin/` (mới) | `01-List-All-Goals`, `02-Get-Goal-Detail`, `03-Delete-Goal` | `GET /goals/admin/all`, `/admin/detail/{id}`, `DELETE /admin/{id}` (`revert_transactions`, `delete_wallet`) |
| `13-Groups/Member/` | `14-List-My-Group-Transactions`, `15-List-Transaction-Participants` | `GET .../transactions/mine`, `.../transactions/{txnId}/participants` |
| `13-Groups/Owner/` | `21-List-All-Group-Transactions`, `22-Add-Members` (body `member_ids`), `23-Reject-Member`, `24-Approve-All-Members`, `25-Reject-All-Members` | `GET .../transactions/all`, `POST .../members`, `.../members/{id}/reject`, `.../approves`, `.../rejects` |
| `15-Payments/Public/` (mới) | `01-PayOS-Webhook`, `02-PayOS-Return`, `03-PayOS-Cancel` | `POST/GET /public/payments/payos/webhook|return|cancel` |

## 2. Lý do thay đổi

- Bruno là tài liệu chạy được của API; file trỏ endpoint đã bỏ sẽ trả 404/405, file sai tên field sẽ trả 400 (Jackson `snake_case`, quy tắc 2.4 `spring-boot_struct.md`).
- Tham số phân trang sai tên (`size`) bị `WebConfig.java:31` bỏ qua, luôn rơi về 20 dòng/trang.
- Nguyên nhân gốc là controller đổi mà không cập nhật Bruno; không có code Java nào sai trong plan này.

## 3. Hướng giải quyết / code xem trước

1. Xoá 26 file mục 1.1 (`git rm`, riêng `12-AI` xoá cả thư mục). Các file đã sửa/untracked trong working tree sẽ không bị đụng ngoài danh sách.
2. Sửa 24 file mục 1.2 bằng `Edit`, đối chiếu từng DTO trước khi sửa. Ví dụ `04-Create-Wallet.yml`:
   ```yaml
   body:
     data: |-
       {
         "name": "Ví tiền mặt",
         "type": "CASH",
         "balance": 500000,
         "icon_code": "{{iconCode}}",
         "color": "#2ECC71"
       }
   ```
3. Tạo 40 file mục 1.3, copy khung từ file cùng module (header, `settings`, `docs` tiếng Việt), body lấy từ DTO. Script `postResponse` lưu `debtId`/`goalId`... chỉ giữ ở file Create đã có.
4. Chạy lại script đối chiếu controller ↔ bruno, kỳ vọng 0 thiếu / 0 thừa.
5. ⚠️ Không đổi API contract, không đổi code Java, không đổi schema.

## 4. Danh sách công việc chi tiết

1. Xoá file mồ côi (mục 1.1).
2. Sửa nhóm Groups (10 file).
3. Sửa nhóm Wallets (4 file) và Transactions (3 file).
4. Sửa Budgets, Goals, Icons, Reports (4 file), phân trang `page_size` ở 2 file Recurring.
5. Đồng nhất `adminAccessToken` + bổ sung `Local.yml`.
6. Thêm Bruno admin: Wallets, Transactions, Budgets, Debts, Goals.
7. Thêm Bruno còn lại: Users/Admin, Wallets/User, Recurring/User, Groups, Payments/Public.
8. Chạy script đối chiếu, `git status`/`git diff --stat` so với mục 1.

## Ngoài phạm vi

Phát hiện khi đọc code, **không sửa** trong plan này:

1. (Đã bỏ) Nhận định ban đầu về `POST /transactions/status` sai do đọc DTO thiếu; DTO có `txn_id`, `status`, `reason` nên `12`/`13` được sửa lại, không xoá.
2. `TxnUpdateReq.java` đặt tên field `occuredAt` (thiếu chữ `r`) → JSON phải gửi `occured_at`, trong khi `TxnCreateReq` là `occurred_at`.
3. `GoalFilterRequest` không có `@BindParam` nên query admin goal phải viết camelCase (`userId`, `targetDateFrom`...), lệch quy ước `snake_case`.
4. `15-Payments/User/test pay.yml` (untracked) gọi URL cứng `http://localhost:8080/v1/payments/200000`.
5. Tên `WalletupdateReq` (chữ `u` thường), `FundKepperUpdateReq`/`fund-kepper` (sai chính tả `keeper`).

## Kiểm tra

- Bạn chọn: có viết test không? → Đề xuất **không** (chỉ đổi `.yml`).
- Claude chạy script đối chiếu `controller ↔ bruno` (Python, 2 file trong thư mục tạm) — kỳ vọng "THIEU = 0, THUA = 0" và không còn lỗi tên field.
- `grep` không còn tên cũ:
  ```bash
  grep -rnE "initial_balance|include_in_total|include_shared|only_in_total|bulk-confirm|bulk-reject|transfer-ownership|/ai/|admin_access|invite_code|size=" bruno --include=*.yml
  ```
- Bạn tự mở Bruno chạy lại các request đã sửa (cần backend chạy; hiện app đang lỗi `NoClassDefFoundError`, cần `mvn clean compile` trước).
- Không chạy test Java; cần kết quả full thì bạn tự chạy.

## Câu hỏi còn mở

Không có.

## Bảng công việc

| # | Công việc | Trạng thái |
|---|---|---|
| 1 | Xoá file mồ côi (24 file; `12`/`13` Groups chuyển sang sửa lại) | Hoàn thành |
| 2 | Sửa nhóm Groups | Hoàn thành |
| 3 | Sửa Wallets + Transactions | Hoàn thành |
| 4 | Sửa Budgets, Goals, Icons, Reports, Recurring (phân trang) | Hoàn thành |
| 5 | Đồng nhất `adminAccessToken`, bổ sung `Local.yml` | Hoàn thành |
| 6 | Thêm Bruno admin (Wallets, Transactions, Budgets, Debts, Goals) | Hoàn thành |
| 7 | Thêm Bruno còn lại (Users, Wallets, Recurring, Groups, Payments) | Hoàn thành |
| 8 | Chạy script đối chiếu + đối chiếu `git diff` | Hoàn thành |

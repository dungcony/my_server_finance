# Sửa lỗi cú pháp YAML và đồng bộ biến admin_access trong bộ test Bruno

## Bối cảnh

1. Khi chạy kiểm thử toàn bộ API qua Bruno CLI (`npx @usebruno/cli run . -r --env Local`), hệ thống báo lỗi `YAMLParseError: Nested mappings are not allowed in compact mappings` và bỏ qua 4 file request trong thư mục `bruno/09-Recurring/Admin/`.
2. Hầu hết các API Admin bị lỗi `401 Unauthorized` do các file request đang dùng biến `{{adminAccessToken}}`, trong khi môi trường `Local.yml` của người dùng lưu biến `admin_access`. Người dùng yêu cầu đồng bộ toàn bộ sang `admin_access` và xoá bỏ `adminAccessToken`.

## Quyết định đã chốt

- **Đồng ý và tiếp thu comment của người dùng**: Giữ nguyên tên biến `admin_access` trong `Local.yml`. Thay thế toàn bộ `adminAccessToken` thành `admin_access` ở tất cả 56 file request Admin và cập nhật biến trong `Production.yml`.
- **Cú pháp YAML 4 file Recurring**: Bọc chuỗi giá trị của `docs:` trong dấu ngoặc kép `""` ở 4 file `09-Recurring/Admin/` để tránh lỗi nested mapping parser.

## 1. Các file sẽ thay đổi

**Tài liệu / Cấu hình Bruno**:

### A. Sửa lỗi cú pháp YAML (4 file):
| STT | File | Vị trí | Nội dung sửa |
|---|---|---|---|
| 1 | `bruno/09-Recurring/Admin/01-Get-Recurring.yml` | Dòng 16 | Bọc chuỗi `docs:` trong `""` |
| 2 | `bruno/09-Recurring/Admin/02-List-Schedules.yml` | Dòng 16 | Bọc chuỗi `docs:` trong `""` |
| 3 | `bruno/09-Recurring/Admin/03-Delete-Recurring.yml` | Dòng 16 | Bọc chuỗi `docs:` trong `""` |
| 4 | `bruno/09-Recurring/Admin/04-Delete-Schedule.yml` | Dòng 16 | Bọc chuỗi `docs:` trong `""` |

### B. Thay đổi biến `adminAccessToken` thành `admin_access`:
Thay đổi lặp lại:
| Vị trí | Từ | Thành |
|---|---|---|
| Dòng header Authorization ở 56 file request Admin | `Bearer {{adminAccessToken}}` hoặc `"Bearer {{adminAccessToken}}"` | `Bearer {{admin_access}}` hoặc `"Bearer {{admin_access}}"` |
| `bruno/01-Auth/Admin/01-Login-Admin.yml:27` | `adminAccessToken` | `admin_access` |
| `bruno/environments/Production.yml:33` | `name: adminAccessToken` | `name: admin_access` |

*Chi tiết các file request Admin đã đổi biến:*
- `bruno/01-Auth/Admin/`: `01-Login-Admin.yml`, `02-Logout-Admin.yml` (2 files)
- `bruno/02-Users/Admin/`: `01-Block-User.yml` đến `11-Unlock-User.yml`, `khôi phục user.yml` (12 files)
- `bruno/03-Wallets/Admin/`: `01-List-User-Wallets.yml` đến `07-List-All-Reconciliations.yml` (7 files)
- `bruno/04-Categories/Admin/`: `01-Create-Category.yml` đến `03-Delete-Category.yml` (3 files)
- `bruno/05-Transactions/Admin/`: `01-List-Transactions.yml` đến `05-Delete-Transaction.yml` (5 files)
- `bruno/06-Budgets/Admin/`: `01-List-Budgets.yml` đến `03-Delete-Budget.yml` (3 files)
- `bruno/07-Reports/Admin/`: `01-Search-Export-Jobs.yml` đến `04-User-Report-Summary.yml` (4 files)
- `bruno/08-Debts/Admin/`: `01-List-Debts.yml` đến `09-Delete-Debt.yml` (9 files)
- `bruno/09-Recurring/Admin/`: `01-Get-Recurring.yml` đến `04-Delete-Schedule.yml` (4 files)
- `bruno/10-Goals/Admin/`: `01-List-All-Goals.yml` đến `03-Delete-Goal.yml` (3 files)
- `bruno/14-Icons/Admin/`: `01-Create-Icon.yml` đến `03-Delete-Icon.yml` (3 files)
- `bruno/15-Payments/Admin/`: `02-Payment-Detail.yml` (1 file)
- `bruno/environments/Production.yml` (1 file)

*Các file không thay đổi*: Mã nguồn Java và cơ sở dữ liệu.

## 2. Lý do thay đổi

- **4 file Recurring**: Dòng `docs: Admin: <mô tả>` có dấu `:` ngay sau `Admin` khiến YAML parser hiểu nhầm là compact mapping key, gây lỗi `YAMLParseError: Nested mappings are not allowed in compact mappings`.
- **56 request Admin**: Toàn bộ các request này dùng `{{adminAccessToken}}` nên khi chạy với `Local.yml` (chỉ có `admin_access`), biến này bị rỗng, dẫn đến header `Authorization: Bearer ` gây lỗi `401 Unauthorized`.

## 3. Hướng giải quyết / code xem trước

- Tại 4 file `09-Recurring/Admin/*.yml`:
  ```yaml
  docs: "Admin: xem chi tiết lịch bất kỳ. Quyền RECURRING_TXN_READ."
  ```
- Tại các request Admin (ví dụ `02-Users/Admin/01-Block-User.yml`):
  ```yaml
  headers:
    - name: Authorization
      value: Bearer {{admin_access}}
  ```
- Tại `Production.yml`:
  ```yaml
    - name: admin_access
      value: ""
  ```

## 4. Danh sách công việc chi tiết

- Sửa dòng 16 của 4 file trong `bruno/09-Recurring/Admin/` (bọc chuỗi `docs:` trong `""`).
- Thay thế toàn bộ `adminAccessToken` thành `admin_access` trong 56 file request Admin.
- Đổi tên biến `adminAccessToken` thành `admin_access` trong `bruno/environments/Production.yml`.
- Cập nhật mô tả trong `bruno/01-Auth/Admin/01-Login-Admin.yml`.

## Ngoài phạm vi

- Lỗi HTTP 429 khi chạy Auth requests: Rate Limiting của Spring Boot khi gọi liên tiếp trong thời gian ngắn.
- Lỗi HTTP 400/404 ở các request chi tiết/sửa/xoá: Do các ID mẫu (`groupId`, `walletId`...) chưa có dữ liệu tương ứng trong DB local.

## Kiểm tra

- Luôn hỏi trước khi chạy, không tự ý chạy test nếu không có yêu cầu.
- Lệnh PowerShell kiểm tra Bruno CLI sau khi sửa:
  ```powershell
  cd d:\projects\java\server\bruno
  npx @usebruno/cli run 14-Icons/Admin -r --env Local
  ```
  *(Kỳ vọng: Các API Admin nhận đúng token `admin_access`, không còn lỗi 401 do thiếu token).*
  ```powershell
  cd d:\projects\java\server\bruno
  npx @usebruno/cli run 09-Recurring/Admin -r --env Local
  ```
  *(Kỳ vọng: Không còn cảnh báo YAMLParseError hoặc Skipping invalid file).*

## Câu hỏi còn mở

- Không còn câu hỏi mở.

## Bảng công việc

| STT | Nhiệm vụ | Trạng thái |
|---|---|---|
| 1 | Sửa cú pháp dòng 16 của 4 file trong `09-Recurring/Admin/` | Hoàn thành |
| 2 | Thay `adminAccessToken` thành `admin_access` ở 56 file request Admin | Hoàn thành |
| 3 | Đổi `adminAccessToken` thành `admin_access` trong `Production.yml` | Hoàn thành |
| 4 | Cập nhật docs `01-Login-Admin.yml` | Hoàn thành |
